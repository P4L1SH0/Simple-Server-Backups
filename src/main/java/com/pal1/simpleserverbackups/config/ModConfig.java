package com.pal1.simpleserverbackups.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Manages the mod's configuration: loads it from disk if it exists,
 * or creates a default configuration the first time the mod runs.
 */
public class ModConfig {

    private static final String FILE_NAME = "simple-server-backups.properties";

    private static final String DEFAULT_BACKUPS_FOLDER = "backups";
    private static final int DEFAULT_COMPRESSION_LEVEL = 6;
    private static final int DEFAULT_MAX_BACKUPS = 0; // 0 = no limit
    private static final String DEFAULT_LANGUAGE = "en";
    private static final int DEFAULT_AUTO_BACKUP_INTERVAL_MINUTES = 0; // 0 = disabled
    private static final String DEFAULT_ALLOWED_USERS = "";

    // Hand-written template used only the very first time the config file
    // is created, so we get nicely formatted comments above every option
    // (Properties.store() alone can't control per-key comments or order).
    private static final String DEFAULT_CONFIG_TEMPLATE = """
            # Simple Server Backups Configuration
            #
            # Edit these values to customize the backup system.
            # Use /backup reload to apply changes without restarting the server.

            # Folder where backups will be stored.
            backups-folder=backups

            # Compression level for backup files.
            # Values:
            # 0 = No compression
            # 9 = Maximum compression
            compression-level=6

            # Maximum number of backups that will be kept.
            # 0 = Unlimited backups.
            max-backups=0

            # Language used by the mod.
            # Available languages: en, es
            language=en

            # Automatic backup interval in minutes.
            # 0 = Disabled.
            auto-backup-interval-minutes=0

            # Player names allowed to use /backup even if they are not
            # server operators. Comma-separated, leave empty to disable.
            # Example: allowed-users=Steve, Alex
            allowed-users=
            """;

    private String backupsFolderName;
    private int compressionLevel;
    private int maxBackups;
    private String language;
    private int autoBackupIntervalMinutes;
    private Set<String> allowedUsers;

    public void load() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);

        if (!Files.exists(configPath)) {
            writeDefaultConfigFile(configPath);
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(configPath)) {
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Could not read the configuration file: " + configPath, e);
        }

        this.backupsFolderName = properties.getProperty("backups-folder", DEFAULT_BACKUPS_FOLDER);
        this.compressionLevel = parseIntSafe(properties.getProperty("compression-level"), DEFAULT_COMPRESSION_LEVEL);
        this.maxBackups = parseIntSafe(properties.getProperty("max-backups"), DEFAULT_MAX_BACKUPS);
        this.language = properties.getProperty("language", DEFAULT_LANGUAGE);
        this.autoBackupIntervalMinutes = parseIntSafe(
                properties.getProperty("auto-backup-interval-minutes"), DEFAULT_AUTO_BACKUP_INTERVAL_MINUTES);
        this.allowedUsers = parseAllowedUsers(properties.getProperty("allowed-users", DEFAULT_ALLOWED_USERS));
    }

    /**
     * Writes the nicely commented default template to disk. Only runs the
     * very first time (when the config file doesn't exist yet).
     */
    private void writeDefaultConfigFile(Path configPath) {
        try {
            Files.writeString(configPath, DEFAULT_CONFIG_TEMPLATE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not create the configuration file: " + configPath, e);
        }
    }

    /**
     * Turns the text "name1, name2, name3" into a set of lowercase names
     * (so comparisons are case-insensitive), with no extra whitespace
     * and no empty entries.
     */
    private Set<String> parseAllowedUsers(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Collections.emptySet();
        }

        Set<String> result = new HashSet<>();
        for (String name : rawValue.split(",")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed.toLowerCase());
            }
        }
        return result;
    }

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

    public int getAutoBackupIntervalMinutes() {
        return autoBackupIntervalMinutes;
    }

    /**
     * Player names (lowercase) allowed to use /backup even if they are
     * not server operators. Empty by default.
     */
    public Set<String> getAllowedUsers() {
        return allowedUsers;
    }
}