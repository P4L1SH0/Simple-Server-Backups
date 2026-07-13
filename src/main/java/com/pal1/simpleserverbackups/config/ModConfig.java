package com.pal1.simpleserverbackups.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Handles the mod configuration:
 * Loads it from disk if it exists,
 * or creates a default configuration on first startup.
 */
public class ModConfig {

    private static final String FILE_NAME = "simple-server-backups.properties";

    private static final String DEFAULT_BACKUPS_FOLDER = "backups";
    private static final int DEFAULT_COMPRESSION_LEVEL = 6;
    private static final int DEFAULT_MAX_BACKUPS = 0; // 0 = unlimited
    private static final String DEFAULT_LANGUAGE = "en";
    private static final int DEFAULT_AUTO_BACKUP_INTERVAL_MINUTES = 0; // 0 = disabled

    private String backupsFolderName;
    private int compressionLevel;
    private int maxBackups;
    private String language;
    private int autoBackupIntervalMinutes;


    /**
     * Loads the configuration file.
     * Creates a default one if it does not exist.
     */
    public void load() {

        Path configPath = FabricLoader.getInstance()
                .getConfigDir()
                .resolve(FILE_NAME);

        Properties properties = new Properties();


        if (Files.exists(configPath)) {

            try (InputStream input = Files.newInputStream(configPath)) {
                properties.load(input);

            } catch (IOException e) {
                throw new RuntimeException(
                        "Failed to read configuration file: " + configPath,
                        e
                );
            }

        } else {

            properties.setProperty(
                    "backups-folder",
                    DEFAULT_BACKUPS_FOLDER
            );

            properties.setProperty(
                    "compression-level",
                    String.valueOf(DEFAULT_COMPRESSION_LEVEL)
            );

            properties.setProperty(
                    "max-backups",
                    String.valueOf(DEFAULT_MAX_BACKUPS)
            );

            properties.setProperty(
                    "language",
                    DEFAULT_LANGUAGE
            );

            properties.setProperty(
                    "auto-backup-interval-minutes",
                    String.valueOf(DEFAULT_AUTO_BACKUP_INTERVAL_MINUTES)
            );

            save(configPath, properties);
        }


        this.backupsFolderName = properties.getProperty(
                "backups-folder",
                DEFAULT_BACKUPS_FOLDER
        );

        this.compressionLevel = parseIntSafe(
                properties.getProperty("compression-level"),
                DEFAULT_COMPRESSION_LEVEL
        );

        this.maxBackups = parseIntSafe(
                properties.getProperty("max-backups"),
                DEFAULT_MAX_BACKUPS
        );

        this.language = properties.getProperty(
                "language",
                DEFAULT_LANGUAGE
        );

        this.autoBackupIntervalMinutes = parseIntSafe(
                properties.getProperty("auto-backup-interval-minutes"),
                DEFAULT_AUTO_BACKUP_INTERVAL_MINUTES
        );
    }


    /**
     * Creates the default configuration file with comments.
     */
    private void save(Path configPath, Properties properties) {

        try {

            String config = """
                    # Simple Server Backups Configuration
                    #
                    # Edit these values to customize the backup system.
                    # Use /backup reload to apply changes without restarting the server.
                    
                    
                    # Folder where backups will be stored.
                    backups-folder=%s
                    
                    
                    # Compression level for backup files.
                    # Values:
                    # 0 = No compression
                    # 9 = Maximum compression
                    compression-level=%s
                    
                    
                    # Maximum number of backups that will be kept.
                    # 0 = Unlimited backups.
                    max-backups=%s
                    
                    
                    # Language used by the mod.
                    # Available languages: en, es
                    language=%s
                    
                    
                    # Automatic backup interval in minutes.
                    # 0 = Disabled.
                    auto-backup-interval-minutes=%s
                    """.formatted(
                    properties.getProperty("backups-folder"),
                    properties.getProperty("compression-level"),
                    properties.getProperty("max-backups"),
                    properties.getProperty("language"),
                    properties.getProperty("auto-backup-interval-minutes")
            );


            Files.writeString(configPath, config);


        } catch (IOException e) {

            throw new RuntimeException(
                    "Failed to save configuration file: " + configPath,
                    e
            );
        }
    }


    /**
     * Safely parses an integer value.
     * Returns the default value if parsing fails.
     */
    private int parseIntSafe(String text, int defaultValue) {

        try {
            return Integer.parseInt(text);

        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }


    public String getBackupsFolderName() {
        return backupsFolderName;
    }


    public int getCompressionLevel() {
        return compressionLevel;
    }


    public int getMaxBackups() {
        return maxBackups;
    }


    public String getLanguage() {
        return language;
    }


    /**
     * Minutes between automatic backups.
     * 0 means automatic backups are disabled.
     */
    public int getAutoBackupIntervalMinutes() {
        return autoBackupIntervalMinutes;
    }
}