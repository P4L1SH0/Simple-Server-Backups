package com.pal1.simpleserverbackups.backup;

import com.pal1.simpleserverbackups.SimpleServerBackups;
import com.pal1.simpleserverbackups.lang.LocalizedException;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Handles everything related to managing backups: knowing where they are
 * stored, creating them, listing them, deleting them, and restoring them.
 */
public class BackupManager {

    // File Minecraft keeps locked at the OS level while the server is
    // running. It holds no world data, so we exclude it.
    private static final String LOCK_FILE_NAME = "session.lock";

    // Only letters, numbers, hyphens and underscores. Prevents dangerous
    // names (e.g. containing "../" trying to escape the backups folder)
    // and names that are invalid on Windows.
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_\\-]+");

    /**
     * Returns the path to the backups folder, creating it if it doesn't
     * exist yet.
     */
    public Path getBackupsDirectory() {
        String folderName = SimpleServerBackups.getConfig().getBackupsFolderName();

        // getGameDir() returns the server's root folder (where server.properties,
        // the "world" folder, etc. live).
        Path serverRoot = FabricLoader.getInstance().getGameDir();
        Path backupsDir = serverRoot.resolve(folderName);

        ensureDirectoryExists(backupsDir);

        return backupsDir;
    }

    /**
     * Creates the given directory (and any needed parent directories) if
     * it doesn't exist yet. Does nothing if it already exists.
     */
    private void ensureDirectoryExists(Path directory) {
        if (Files.exists(directory)) {
            return;
        }

        try {
            Files.createDirectories(directory);
            SimpleServerBackups.LOGGER.info("Backups folder created at: {}", directory);
        } catch (IOException e) {
            throw new RuntimeException("Could not create the backups folder at: " + directory, e);
        }
    }

    /**
     * Checks that a backup name is safe and valid. Used both when
     * creating and when deleting, so we never blindly trust a name
     * typed by a user.
     */
    private void validateBackupName(String name) {
        if (name == null || name.isBlank() || !VALID_NAME_PATTERN.matcher(name).matches()) {
            throw new LocalizedException("error.invalid_name", name);
        }
    }

    /**
     * Creates a full backup of the world (all dimensions) with the given name.
     *
     * Safe process:
     *  1. Forces any pending data to be saved (save-all flush) - world chunks.
     *  2. ALSO forces an immediate save of every connected player
     *     (PlayerList.saveAll()) - this is necessary because save-all alone
     *     doesn't always reliably reflect the position/inventory of a
     *     player who is still connected at that exact moment.
     *  3. Disables autosave (save-off), so nothing changes while we compress.
     *  4. Compresses the whole world into a ZIP (skipping OS lock files).
     *  5. No matter what happens (even if compression fails), re-enables
     *     autosave (save-on).
     */
    public BackupResult createBackup(MinecraftServer server, String backupName) throws IOException {
        validateBackupName(backupName);

        long startTime = System.currentTimeMillis();

        CommandSourceStack commandSource = server.createCommandSourceStack();
        Commands commands = server.getCommands();

        // Root folder of the world (Overworld + subfolders for other
        // dimensions, such as the Nether and the End).
        Path worldFolder = server.getWorldPath(LevelResource.ROOT);

        Path backupsDir = getBackupsDirectory();
        Path zipPath = backupsDir.resolve(backupName + ".zip");

        // 1: general save of world chunks.
        commands.performPrefixedCommand(commandSource, "save-all flush");

        // 2: explicit, guaranteed save of every connected player
        // (position, inventory, experience, etc.), regardless of whether
        // save-all already covered it or not.
        server.getPlayerList().saveAll();

        // 3: disable autosave.
        commands.performPrefixedCommand(commandSource, "save-off");

        try {
            // 4: compress the world, with autosave already disabled.
            compressDirectory(worldFolder, zipPath);
        } catch (UncheckedIOException e) {
            // If something failed partway through compression, delete the
            // half-finished ZIP so we don't leave a corrupt backup taking
            // up space and confusing the user.
            Files.deleteIfExists(zipPath);
            throw e.getCause();
        } finally {
            // 5: this ALWAYS runs, whether compression failed or not.
            commands.performPrefixedCommand(commandSource, "save-on");
        }

        long durationMillis = System.currentTimeMillis() - startTime;
        long sizeInBytes = Files.size(zipPath);

        // Apply the maximum backup limit AFTER creating this one, so we
        // never delete the one we just created.
        enforceMaxBackups();

        return new BackupResult(zipPath, sizeInBytes, durationMillis);
    }

    /**
     * If a maximum limit is configured (max-backups > 0) and it has been
     * exceeded, deletes the oldest backups until back within the limit.
     */
    private void enforceMaxBackups() {
        int maxBackups = SimpleServerBackups.getConfig().getMaxBackups();

        if (maxBackups <= 0) {
            return; // 0 = no limit
        }

        try {
            // listBackups() already returns them from newest to oldest.
            List<BackupInfo> backups = listBackups();

            if (backups.size() <= maxBackups) {
                return;
            }

            List<BackupInfo> toDelete = backups.subList(maxBackups, backups.size());

            for (BackupInfo backup : toDelete) {
                Path zipPath = getBackupsDirectory().resolve(backup.name() + ".zip");
                Files.deleteIfExists(zipPath);
                SimpleServerBackups.LOGGER.info(
                        "Old backup automatically deleted (max-backups={} limit): {}", maxBackups, backup.name());
            }
        } catch (IOException e) {
            SimpleServerBackups.LOGGER.error("Error applying the maximum backup limit", e);
        }
    }

    /**
     * Returns the list of existing backups, sorted from newest to oldest.
     */
    public List<BackupInfo> listBackups() throws IOException {
        Path backupsDir = getBackupsDirectory();

        try (Stream<Path> files = Files.list(backupsDir)) {
            return files
                    .filter(path -> path.toString().endsWith(".zip"))
                    .map(this::toBackupInfo)
                    .sorted(Comparator.comparingLong(BackupInfo::lastModifiedMillis).reversed())
                    .collect(Collectors.toList());
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Returns detailed information about a single backup.
     * Throws a clear exception if it doesn't exist.
     */
    public BackupInfo getBackupInfo(String name) throws IOException {
        validateBackupName(name);

        Path zipPath = getBackupsDirectory().resolve(name + ".zip");

        if (!Files.exists(zipPath)) {
            throw new LocalizedException("error.backup_not_found", name);
        }

        return toBackupInfo(zipPath);
    }

    private BackupInfo toBackupInfo(Path zipFile) {
        try {
            String fileName = zipFile.getFileName().toString();
            String name = fileName.substring(0, fileName.length() - ".zip".length());
            long size = Files.size(zipFile);
            long lastModified = Files.getLastModifiedTime(zipFile).toMillis();
            int fileCount = countZipEntries(zipFile);
            return new BackupInfo(name, size, lastModified, fileCount);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Counts how many files (not folders) are inside a ZIP, without
     * needing to extract it.
     */
    private int countZipEntries(Path zipFile) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            return (int) zip.stream().filter(entry -> !entry.isDirectory()).count();
        }
    }

    /**
     * Checks whether a backup with the given name exists.
     */
    public boolean backupExists(String name) {
        validateBackupName(name);
        Path zipPath = getBackupsDirectory().resolve(name + ".zip");
        return Files.exists(zipPath);
    }

    /**
     * Deletes the given backup. Throws a clear exception if the name isn't
     * valid or if no backup exists with that name.
     */
    public void deleteBackup(String name) throws IOException {
        validateBackupName(name);

        Path zipPath = getBackupsDirectory().resolve(name + ".zip");

        if (!Files.exists(zipPath)) {
            throw new LocalizedException("error.backup_not_found", name);
        }

        Files.delete(zipPath);
    }

    /**
     * Recursively compresses everything inside sourceDir into a single
     * ZIP file at zipFilePath, keeping the folder structure (this is why
     * extra dimensions, like the Nether and the End, are automatically
     * included: they are subfolders inside sourceDir).
     */
    private void compressDirectory(Path sourceDir, Path zipFilePath) throws IOException {
        int compressionLevel = SimpleServerBackups.getConfig().getCompressionLevel();

        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            zipOut.setLevel(compressionLevel);

            try (Stream<Path> filesToCompress = Files.walk(sourceDir)) {
                filesToCompress
                        .filter(Files::isRegularFile)
                        .filter(this::isSafeToInclude)
                        .forEach(path -> addFileToZip(sourceDir, path, zipOut));
            }
        }
    }

    /**
     * Discards files that shouldn't be included in the backup, such as
     * the "session.lock" lock file Minecraft keeps open while the server
     * is running. It holds no world data, and Minecraft recreates it
     * automatically when loading any world.
     */
    private boolean isSafeToInclude(Path file) {
        String fileName = file.getFileName().toString();
        return !fileName.equals(LOCK_FILE_NAME);
    }

    /**
     * Adds a single file to the ZIP, computing its relative path inside
     * the world (e.g. "DIM-1/region/r.0.0.mca" instead of the absolute
     * path on disk).
     *
     * Technical note: Files.walk(...).forEach(...) doesn't allow throwing
     * IOException directly inside the lambda, so we wrap it in
     * UncheckedIOException (an "unchecked" version of IOException) and
     * unwrap it further up, in createBackup().
     */
    private void addFileToZip(Path sourceDir, Path file, ZipOutputStream zipOut) {
        String entryName = sourceDir.relativize(file).toString().replace('\\', '/');

        try {
            zipOut.putNextEntry(new ZipEntry(entryName));
            Files.copy(file, zipOut);
            zipOut.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not add to the backup: " + file, e);
        }
    }
}