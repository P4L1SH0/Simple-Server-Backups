package com.pal1.simpleserverbackups.lang;

import com.pal1.simpleserverbackups.SimpleServerBackups;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Properties;

/**
 * Carga los textos del mod en el idioma configurado y permite pedirlos
 * por clave, sustituyendo valores variables (marcadores {0}, {1}...).
 *
 * Los archivos de idioma viven dentro del propio mod, en
 * src/main/resources/lang/es.properties y lang/en.properties.
 */
public class Messages {

    private static final String DEFAULT_LANGUAGE = "es";

    private static Properties messages;

    /**
     * Carga (o recarga) el idioma indicado. Si no existe un archivo para
     * ese idioma, usa espa\u00f1ol como respaldo y avisa en el log.
     */
    public static void load(String language) {
        String lang = (language == null || language.isBlank()) ? DEFAULT_LANGUAGE : language.toLowerCase();

        Properties loaded = readLangFile(lang);

        if (loaded == null) {
            SimpleServerBackups.LOGGER.warn(
                    "Idioma '{}' no soportado, usando '{}' por defecto.", lang, DEFAULT_LANGUAGE);
            loaded = readLangFile(DEFAULT_LANGUAGE);
        }

        messages = (loaded != null) ? loaded : new Properties();
    }

    private static Properties readLangFile(String lang) {
        String resourcePath = "/lang/" + lang + ".properties";

        try (InputStream input = Messages.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                return null;
            }
            Properties properties = new Properties();
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            return properties;
        } catch (IOException e) {
            SimpleServerBackups.LOGGER.error("Error al leer el archivo de idioma '{}'", resourcePath, e);
            return null;
        }
    }

    /**
     * Devuelve el texto de una clave en el idioma cargado, sustituyendo
     * {0}, {1}... por los valores indicados. Si la clave no existe,
     * devuelve la propia clave (para detectar fallos facilmente).
     */
    public static String get(String key, Object... args) {
        if (messages == null) {
            load(DEFAULT_LANGUAGE);
        }

        String template = messages.getProperty(key, key);
        return MessageFormat.format(template, args);
    }
}