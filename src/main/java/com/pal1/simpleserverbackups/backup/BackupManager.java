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
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Handles everything related to backup management: knowing where they
 * are stored, creating, listing, deleting and restoring them.
 */
public class BackupManager {

    // File that Minecraft locks at the OS level while the server is
    // running. It holds no world data, so we exclude it from backups.
    private static final String LOCK_FILE_NAME = "session.lock";

    // Only letters, numbers, hyphens and underscores. Avoids dangerous
    // names (e.g. with "../" trying to escape the backups folder) and
    // names that are invalid as filenames on Windows.
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_\\-]+");

    /**
     * Returns the path to the backups folder, creating it if it doesn't exist yet.
     */
    public Path getBackupsDirectory() {
        String folderName = SimpleServerBackups.getConfig().getBackupsFolderName();

        Path serverRoot = FabricLoader.getInstance().getGameDir();
        Path backupsDir = serverRoot.resolve(folderName);

        ensureDirectoryExists(backupsDir);

        return backupsDir;
    }

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
     * creating and when deleting, so we never blindly trust a
     * user-supplied name.
     */
    private void validateBackupName(String name) {
        if (name == null || name.isBlank() || !VALID_NAME_PATTERN.matcher(name).matches()) {
            throw new LocalizedException("error.invalid_name", name);
        }
    }

    /**
     * Creates a full backup of the world (all dimensions) with the given name,
     * WITHOUT blocking the main server thread while the heavy compression work runs.
     *
     * Safe process:
     *  1. (main thread) Forces any pending world data to be saved (save-all flush).
     *  2. (main thread) Forces an immediate save of every connected player.
     *  3. (main thread) Disables autosaving (save-off), so nothing changes
     *     while we read and compress the world files.
     *  4. (background thread) Compresses the whole world into a ZIP. This is
     *     the slow, CPU/IO-heavy part, so it must NOT run on the main thread,
     *     or the whole server would freeze (causing lag and client timeouts)
     *     while it's working.
     *  5. (main thread again) Whatever happened, re-enables autosaving
     *     (save-on), then reports the result via the given callbacks.
     *
     * @param server    the running server (needed for save commands and to
     *                  safely hop back to the main thread afterwards).
     * @param backupName name of the backup, WITHOUT the ".zip" extension.
     * @param onSuccess  called on the main thread once the backup finished
     *                   successfully, with its result (path, size, duration).
     * @param onError    called on the main thread if anything failed.
     */
    public void createBackupAsync(MinecraftServer server, String backupName,
                                  Consumer<BackupResult> onSuccess, Consumer<Exception> onError) {
        // Validated immediately (fast, no I/O), so obviously invalid names
        // are rejected right away instead of silently starting a thread.
        validateBackupName(backupName);

        long startTime = System.currentTimeMillis();

        CommandSourceStack commandSource = server.createCommandSourceStack();
        Commands commands = server.getCommands();

        Path worldFolder = server.getWorldPath(LevelResource.ROOT);
        Path backupsDir = getBackupsDirectory();
        Path zipPath = backupsDir.resolve(backupName + ".zip");

        // 1: general saving of world chunks.
        commands.performPrefixedCommand(commandSource, "save-all flush");

        // 2: explicit, guaranteed save of every connected player.
        server.getPlayerList().saveAll();

        // 3: disable autosaving.
        commands.performPrefixedCommand(commandSource, "save-off");

        // 4: compress the world in a separate thread, so the server keeps
        // ticking normally (no lag, no timeouts) while this runs.
        Thread compressionThread = new Thread(() -> {
            try {
                compressDirectory(worldFolder, zipPath);

                long durationMillis = System.currentTimeMillis() - startTime;
                long sizeInBytes = Files.size(zipPath);

                // 5: back on the main thread, re-enable autosave and report success.
                server.execute(() -> {
                    commands.performPrefixedCommand(commandSource, "save-on");
                    enforceMaxBackups();
                    onSuccess.accept(new BackupResult(zipPath, sizeInBytes, durationMillis));
                });
            } catch (Exception e) {
                // If something failed halfway through compression, delete the
                // partial ZIP so we don't leave a corrupt backup taking up
                // space and confusing the user.
                try {
                    Files.deleteIfExists(zipPath);
                } catch (IOException ignored) {
                    // Nothing more we can do about a leftover partial file here.
                }

                // 5 (failure case): back on the main thread, re-enable
                // autosave and report the error.
                server.execute(() -> {
                    commands.performPrefixedCommand(commandSource, "save-on");
                    onError.accept(e);
                });
            }
        }, "simple-server-backups-compress-" + backupName);

        compressionThread.start();
    }

    /**
     * If a maximum limit is configured (max-backups > 0) and it has been
     * exceeded, deletes the oldest backups until we're back within the limit.
     */
    private void enforceMaxBackups() {
        int maxBackups = SimpleServerBackups.getConfig().getMaxBackups();

        if (maxBackups <= 0) {
            return; // 0 = no limit
        }

        try {
            // listBackups() already returns from newest to oldest.
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
            SimpleServerBackups.LOGGER.error("Error while enforcing the max-backups limit", e);
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
     * Counts how many files (not folders) are inside a ZIP,
     * without needing to extract it.
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
     * Deletes the given backup. Throws a clear exception if the name
     * isn't valid or if no backup exists with that name.
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
     * ZIP file at zipFilePath, keeping the subfolder structure (this is
     * why extra dimensions, like the Nether and the End, are included
     * automatically: they are subfolders inside sourceDir).
     *
     * IMPORTANT: this method does real disk I/O and CPU work (compression),
     * and can take several seconds for a large world. It must always be
     * called from a background thread, never from the main server thread.
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
     * Discards files that must not be included in the backup, such as the
     * "session.lock" file that Minecraft keeps open while the server is
     * running. It holds no world data, and Minecraft automatically
     * recreates it whenever any world is loaded.
     */
    private boolean isSafeToInclude(Path file) {
        String fileName = file.getFileName().toString();
        return !fileName.equals(LOCK_FILE_NAME);
    }

    /**
     * Adds a single file to the ZIP, computing its path relative to the
     * world folder (e.g. "DIM-1/region/r.0.0.mca" instead of the
     * absolute path on disk).
     *
     * Technical note: Files.walk(...).forEach(...) doesn't allow throwing
     * IOException directly inside the lambda, so we wrap it in
     * UncheckedIOException (an "unchecked" version of IOException) and
     * unwrap it further up.
     */
    private void addFileToZip(Path sourceDir, Path file, ZipOutputStream zipOut) {
        String entryName = sourceDir.relativize(file).toString().replace('\\', '/');

        try {
            zipOut.putNextEntry(new ZipEntry(entryName));
            Files.copy(file, zipOut);
            zipOut.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not add file to backup: " + file, e);
        }
    }
}