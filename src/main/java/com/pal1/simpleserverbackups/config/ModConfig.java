package com.pal1.simpleserverbackups.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Gestiona la configuracion del mod: la carga desde disco si existe,
 * o crea una configuracion por defecto si es la primera vez que se ejecuta.
 */
public class ModConfig {

    private static final String FILE_NAME = "simple-server-backups.properties";

    private static final String DEFAULT_BACKUPS_FOLDER = "backups";
    private static final int DEFAULT_COMPRESSION_LEVEL = 6;
    private static final int DEFAULT_MAX_BACKUPS = 0; // 0 = sin limite
    private static final String DEFAULT_LANGUAGE = "en";
    private static final int DEFAULT_AUTO_BACKUP_INTERVAL_MINUTES = 0; // 0 = desactivado

    private String backupsFolderName;
    private int compressionLevel;
    private int maxBackups;
    private String language;
    private int autoBackupIntervalMinutes;

    public void load() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        Properties properties = new Properties();

        if (Files.exists(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                properties.load(input);
            } catch (IOException e) {
                throw new RuntimeException("No se pudo leer el archivo de configuración: " + configPath, e);
            }
        } else {
            properties.setProperty("backups-folder", DEFAULT_BACKUPS_FOLDER);
            properties.setProperty("compression-level", String.valueOf(DEFAULT_COMPRESSION_LEVEL));
            properties.setProperty("max-backups", String.valueOf(DEFAULT_MAX_BACKUPS));
            properties.setProperty("language", DEFAULT_LANGUAGE);
            properties.setProperty("auto-backup-interval-minutes", String.valueOf(DEFAULT_AUTO_BACKUP_INTERVAL_MINUTES));
            save(configPath, properties);
        }

        this.backupsFolderName = properties.getProperty("backups-folder", DEFAULT_BACKUPS_FOLDER);
        this.compressionLevel = parseIntSafe(properties.getProperty("compression-level"), DEFAULT_COMPRESSION_LEVEL);
        this.maxBackups = parseIntSafe(properties.getProperty("max-backups"), DEFAULT_MAX_BACKUPS);
        this.language = properties.getProperty("language", DEFAULT_LANGUAGE);
        this.autoBackupIntervalMinutes = parseIntSafe(
                properties.getProperty("auto-backup-interval-minutes"), DEFAULT_AUTO_BACKUP_INTERVAL_MINUTES);
    }

    private void save(Path configPath, Properties properties) {
        try (OutputStream output = Files.newOutputStream(configPath)) {
            properties.store(output, "Configuracion de Simple Server Backups");
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar el archivo de configuración: " + configPath, e);
        }
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
     * Minutos entre cada backup automatico. 0 significa desactivado
     * (por defecto), en cuyo caso el admin debe crear los backups
     * manualmente con los comandos.
     */
    public int getAutoBackupIntervalMinutes() {
        return autoBackupIntervalMinutes;
    }
}