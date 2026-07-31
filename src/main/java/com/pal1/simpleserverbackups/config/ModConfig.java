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
    private static final String DEFAULT_AUTO_BACKUP_TIME = ""; // empty = disabled
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

            # Time of day (24-hour clock, HH:mm) at which an automatic
            # backup will be created every day. Leave empty to disable
            # automatic backups.
            # If the server is offline at that exact time, that day's
            # backup will simply be skipped. If the server starts up
            # AFTER this time and today's backup hasn't run yet, it
            # will run shortly after startup instead of waiting for
            # tomorrow.
            # Example: auto-backup-time=04:00
            auto-backup-time=

            # Players allowed to use /backup even if they are not server
            # operators. Comma-separated, leave empty to disable.
            #
            # You can list either player names or player UUIDs.
            # UUIDs are strongly recommended on premium (online-mode) servers:
            # they are tied to the real account and cannot be impersonated,
            # and keep working even if the player changes their username.
            # On non-premium (offline-mode) servers, neither names nor UUIDs
            # are truly secure, since the server cannot verify who is really
            # connecting; use server operators (/op) for anything sensitive.
            #
            # Example: allowed-users=Steve, 11222710-16a9-42b8-8763-c7b0856c0653
            allowed-users=
            """;

    private String backupsFolderName;
    private int compressionLevel;
    private int maxBackups;
    private String language;
    private String autoBackupTime;
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
        this.autoBackupTime = properties.getProperty("auto-backup-time", DEFAULT_AUTO_BACKUP_TIME).trim();
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
     * Turns the text "value1, value2, value3" into a set of lowercase
     * values (so comparisons are case-insensitive), with no extra
     * whitespace and no empty entries. Each value can be either a player
     * name or a player UUID - both are just compared as plain lowercase
     * text against the connecting player's name and UUID.
     */
    private Set<String> parseAllowedUsers(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Collections.emptySet();
        }

        Set<String> result = new HashSet<>();
        for (String value : rawValue.split(",")) {
            String trimmed = value.trim();
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

    /**
     * Time of day (as text, e.g. "04:00") at which an automatic backup
     * should run every day. Empty means automatic backups are disabled.
     */
    public String getAutoBackupTime() {
        return autoBackupTime;
    }

    /**
     * Player names and/or UUIDs (lowercase) allowed to use /backup even
     * if they are not server operators. Empty by default.
     */
    public Set<String> getAllowedUsers() {
        return allowedUsers;
    }
}