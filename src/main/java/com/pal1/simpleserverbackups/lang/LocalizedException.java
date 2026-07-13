package com.pal1.simpleserverbackups.lang;

/**
 * Excepcion que lleva una CLAVE de traduccion (y sus valores variables)
 * en vez de un texto ya fijo en un idioma concreto. Asi, quien la capture
 * (normalmente BackupCommand) puede traducirla al idioma configurado
 * antes de mostrarla al usuario.
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