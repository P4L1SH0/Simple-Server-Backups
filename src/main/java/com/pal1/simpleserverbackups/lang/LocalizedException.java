package com.pal1.simpleserverbackups.lang;

/**
 * Exception that carries a translation KEY (and its variable values)
 * instead of text already fixed in one language. This way, whoever
 * catches it (usually BackupCommand) can translate it into the
 * configured language before showing it to the user.
 */
public class LocalizedException extends RuntimeException {

    private final String key;
    private final Object[] args;

    public LocalizedException(String key, Object... args) {
        super(key);
        this.key = key;
        this.args = args;
    }

    public String getKey() {
        return key;
    }

    public Object[] getArgs() {
        return args;
    }
}