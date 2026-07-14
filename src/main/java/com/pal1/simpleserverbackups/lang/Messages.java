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
 * Loads the mod's chat messages in the configured language and provides
 * them by key, substituting variable placeholders ({0}, {1}...).
 *
 * The language files live inside the mod itself, at
 * src/main/resources/lang/es.properties and lang/en.properties.
 */
public class Messages {

    private static final String DEFAULT_LANGUAGE = "en";

    private static Properties messages;

    /**
     * Loads (or reloads) the given language. If no file exists for that
     * language, falls back to English and logs a warning.
     */
    public static void load(String language) {
        String lang = (language == null || language.isBlank()) ? DEFAULT_LANGUAGE : language.toLowerCase();

        Properties loaded = readLangFile(lang);

        if (loaded == null) {
            SimpleServerBackups.LOGGER.warn(
                    "Language '{}' not supported, falling back to '{}'.", lang, DEFAULT_LANGUAGE);
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
            SimpleServerBackups.LOGGER.error("Error reading language file '{}'", resourcePath, e);
            return null;
        }
    }

    /**
     * Returns the text for a key in the loaded language, substituting
     * {0}, {1}... with the given values. If the key doesn't exist,
     * returns the key itself (so missing translations are easy to spot).
     */
    public static String get(String key, Object... args) {
        if (messages == null) {
            load(DEFAULT_LANGUAGE);
        }

        String template = messages.getProperty(key, key);
        return MessageFormat.format(template, args);
    }
}