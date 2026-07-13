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
 * Gestiona la restauracion de backups.
 *
 * Restaurar el mundo con el servidor en marcha no es seguro (el servidor
 * tiene el mundo cargado en memoria y acabaria sobrescribiendo la restauracion).
 * Por eso, en lugar de restaurar al momento, este gestor:
 *
 *  1. Guarda un "marcador" en disco indicando que hay una restauracion pendiente.
 *  2. En el SIGUIENTE arranque del servidor, ANTES de que Minecraft cargue
 *     el mundo, comprueba si existe ese marcador y, si es asi, sustituye
 *     la carpeta del mundo por el contenido del backup.
 */
public class RestoreManager {

    private static final String MARKER_FILE_NAME = "simple-server-backups-pending-restore.txt";

    /**
     * Marca un backup para ser restaurado en el proximo arranque del servidor.
     * No toca el mundo actual en absoluto todavia.
     */
    public void requestRestore(String backupName) throws IOException {
        Path markerPath = FabricLoader.getInstance().getConfigDir().resolve(MARKER_FILE_NAME);
        Files.writeString(markerPath, backupName, StandardCharsets.UTF_8);
    }

    /**
     * Comprueba si hay una restauracion pendiente y, si la hay, la aplica.
     * Debe llamarse SIEMPRE al inicio de onInitialize(), antes de que
     * Minecraft empiece a preparar el mundo.
     */
    public void applyPendingRestoreIfNeeded() {
        Path markerPath = FabricLoader.getInstance().getConfigDir().resolve(MARKER_FILE_NAME);

        if (!Files.exists(markerPath)) {
            return;
        }

        try {
            String backupName = Files.readString(markerPath, StandardCharsets.UTF_8).trim();
            SimpleServerBackups.LOGGER.info("Restauracion pendiente detectada: '{}'. Restaurando...", backupName);

            Path backupZip = new BackupManager().getBackupsDirectory().resolve(backupName + ".zip");

            if (!Files.exists(backupZip)) {
                SimpleServerBackups.LOGGER.error(
                        "No se encontro el backup '{}' para restaurar. Se cancela la restauracion y el servidor arrancara con el mundo actual.",
                        backupName);
                Files.deleteIfExists(markerPath);
                return;
            }

            Path worldFolder = resolveWorldFolder();

            deleteDirectoryContents(worldFolder);
            extractZip(backupZip, worldFolder);

            Files.delete(markerPath);
            SimpleServerBackups.LOGGER.info("Backup '{}' restaurado correctamente.", backupName);
        } catch (IOException e) {
            throw new RuntimeException("Error al restaurar el backup pendiente", e);
        }
    }

    /**
     * Averigua la carpeta del mundo leyendo "level-name" de server.properties.
     * En este punto tan temprano del arranque, el servidor todavia no existe,
     * asi que no podemos usar server.getWorldPath(...) (eso solo funciona
     * una vez el servidor ya esta en marcha).
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
     * Borra todo el contenido de una carpeta (pero no la carpeta en si),
     * para dejar sitio limpio antes de extraer el backup.
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
     * Extrae un archivo ZIP completo dentro de la carpeta indicada.
     */
    private void extractZip(Path zipPath, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);

        try (ZipInputStream zipIn = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                Path outputPath = targetDir.resolve(entry.getName()).normalize();

                // Proteccion de seguridad: evita que un ZIP manipulado pueda
                // escribir fuera de la carpeta del mundo ("zip slip").
                if (!outputPath.startsWith(targetDir)) {
                    throw new IOException("Entrada de ZIP no segura: " + entry.getName());
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