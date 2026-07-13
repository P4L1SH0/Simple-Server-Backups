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
 * Se encarga de todo lo relacionado con la gestion de backups:
 * saber donde se guardan, crearlos, listarlos, borrarlos y restaurarlos.
 */
public class BackupManager {

    private static final String LOCK_FILE_NAME = "session.lock";

    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_\\-]+");

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
            SimpleServerBackups.LOGGER.info("Carpeta de backups creada en: {}", directory);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear la carpeta de backups en: " + directory, e);
        }
    }

    private void validateBackupName(String name) {
        if (name == null || name.isBlank() || !VALID_NAME_PATTERN.matcher(name).matches()) {
            throw new LocalizedException("error.invalid_name", name);
        }
    }

    public BackupResult createBackup(MinecraftServer server, String backupName) throws IOException {
        validateBackupName(backupName);

        long startTime = System.currentTimeMillis();

        CommandSourceStack commandSource = server.createCommandSourceStack();
        Commands commands = server.getCommands();

        Path worldFolder = server.getWorldPath(LevelResource.ROOT);
        Path backupsDir = getBackupsDirectory();
        Path zipPath = backupsDir.resolve(backupName + ".zip");

        commands.performPrefixedCommand(commandSource, "save-all flush");
        server.getPlayerList().saveAll();
        commands.performPrefixedCommand(commandSource, "save-off");

        try {
            compressDirectory(worldFolder, zipPath);
        } catch (UncheckedIOException e) {
            Files.deleteIfExists(zipPath);
            throw e.getCause();
        } finally {
            commands.performPrefixedCommand(commandSource, "save-on");
        }

        long durationMillis = System.currentTimeMillis() - startTime;
        long sizeInBytes = Files.size(zipPath);

        enforceMaxBackups();

        return new BackupResult(zipPath, sizeInBytes, durationMillis);
    }

    private void enforceMaxBackups() {
        int maxBackups = SimpleServerBackups.getConfig().getMaxBackups();

        if (maxBackups <= 0) {
            return;
        }

        try {
            List<BackupInfo> backups = listBackups();

            if (backups.size() <= maxBackups) {
                return;
            }

            List<BackupInfo> toDelete = backups.subList(maxBackups, backups.size());

            for (BackupInfo backup : toDelete) {
                Path zipPath = getBackupsDirectory().resolve(backup.name() + ".zip");
                Files.deleteIfExists(zipPath);
                SimpleServerBackups.LOGGER.info(
                        "Backup antiguo eliminado automaticamente (limite max-backups={}): {}", maxBackups, backup.name());
            }
        } catch (IOException e) {
            SimpleServerBackups.LOGGER.error("Error al aplicar el limite maximo de backups", e);
        }
    }

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

    private int countZipEntries(Path zipFile) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            return (int) zip.stream().filter(entry -> !entry.isDirectory()).count();
        }
    }

    public boolean backupExists(String name) {
        validateBackupName(name);
        Path zipPath = getBackupsDirectory().resolve(name + ".zip");
        return Files.exists(zipPath);
    }

    public void deleteBackup(String name) throws IOException {
        validateBackupName(name);

        Path zipPath = getBackupsDirectory().resolve(name + ".zip");

        if (!Files.exists(zipPath)) {
            throw new LocalizedException("error.backup_not_found", name);
        }

        Files.delete(zipPath);
    }

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

    private boolean isSafeToInclude(Path file) {
        String fileName = file.getFileName().toString();
        return !fileName.equals(LOCK_FILE_NAME);
    }

    private void addFileToZip(Path sourceDir, Path file, ZipOutputStream zipOut) {
        String entryName = sourceDir.relativize(file).toString().replace('\\', '/');

        try {
            zipOut.putNextEntry(new ZipEntry(entryName));
            Files.copy(file, zipOut);
            zipOut.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo añadir al backup: " + file, e);
        }
    }
}