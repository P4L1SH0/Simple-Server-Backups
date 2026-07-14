package com.pal1.simpleserverbackups.restore;

import com.pal1.simpleserverbackups.SimpleServerBackups;
import com.pal1.simpleserverbackups.backup.BackupManager;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Handles backup restoration.
 *
 * Restoring the world while the server is running isn't safe (the server
 * has the world loaded in memory and would end up overwriting the
 * restoration). That's why, instead of restoring right away, this
 * manager:
 *
 *  1. Writes a "marker" on disk stating that a restore is pending.
 *  2. On the NEXT server startup, BEFORE Minecraft loads the world,
 *     checks whether that marker exists and, if so, replaces the world
 *     folder with the contents of the backup.
 */
public class RestoreManager {

    private static final String MARKER_FILE_NAME = "simple-server-backups-pending-restore.txt";

    /**
     * Marks a backup to be restored on the next server startup.
     * Does not touch the current world at all yet.
     */
    public void requestRestore(String backupName) throws IOException {
        Path markerPath = FabricLoader.getInstance().getConfigDir().resolve(MARKER_FILE_NAME);
        Files.writeString(markerPath, backupName, StandardCharsets.UTF_8);
    }

    /**
     * Checks whether a restore is pending and, if so, applies it.
     * Must ALWAYS be called at the start of onInitialize(), before
     * Minecraft starts preparing the world.
     */
    public void applyPendingRestoreIfNeeded() {
        Path markerPath = FabricLoader.getInstance().getConfigDir().resolve(MARKER_FILE_NAME);

        if (!Files.exists(markerPath)) {
            return;
        }

        try {
            String backupName = Files.readString(markerPath, StandardCharsets.UTF_8).trim();
            SimpleServerBackups.LOGGER.info("Pending restore detected: '{}'. Restoring...", backupName);

            Path backupZip = new BackupManager().getBackupsDirectory().resolve(backupName + ".zip");

            if (!Files.exists(backupZip)) {
                SimpleServerBackups.LOGGER.error(
                        "Backup '{}' to restore was not found. Cancelling the restore, the server will start with the current world.",
                        backupName);
                Files.deleteIfExists(markerPath);
                return;
            }

            Path worldFolder = resolveWorldFolder();

            deleteDirectoryContents(worldFolder);
            extractZip(backupZip, worldFolder);

            Files.delete(markerPath);
            SimpleServerBackups.LOGGER.info("Backup '{}' restored successfully.", backupName);
        } catch (IOException e) {
            throw new RuntimeException("Error restoring the pending backup", e);
        }
    }

    /**
     * Figures out the world folder by reading "level-name" from
     * server.properties. At this very early point in startup, the server
     * object doesn't exist yet, so we can't use server.getWorldPath(...)
     * (that only works once the server is already running).
     */
    private Path resolveWorldFolder() throws IOException {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path serverPropertiesPath = gameDir.resolve("server.properties");

        String levelName = "world";

        if (Files.exists(serverPropertiesPath)) {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(serverPropertiesPath)) {
                properties.load(input);
            }
            levelName = properties.getProperty("level-name", "world");
        }

        return gameDir.resolve(levelName);
    }

    /**
     * Deletes all the contents of a folder (but not the folder itself),
     * to leave a clean spot before extracting the backup.
     */
    private void deleteDirectoryContents(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(directory))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Extracts a full ZIP file into the given folder.
     */
    private void extractZip(Path zipPath, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);

        try (ZipInputStream zipIn = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                Path outputPath = targetDir.resolve(entry.getName()).normalize();

                // Safety check: prevents a manipulated ZIP from writing
                // outside the world folder ("zip slip").
                if (!outputPath.startsWith(targetDir)) {
                    throw new IOException("Unsafe ZIP entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    Files.copy(zipIn, outputPath, StandardCopyOption.REPLACE_EXISTING);
                }

                zipIn.closeEntry();
            }
        }
    }
}