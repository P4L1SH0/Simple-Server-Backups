package com.pal1.simpleserverbackups.backup;

import java.nio.file.Path;

/**
 * Representa el resultado de un backup ya terminado: donde quedo el archivo,
 * cuanto pesa y cuanto tiempo tardo en crearse. Se usa para construir los
 * mensajes que se muestran al usuario ("Backup completado", tamaño, tiempo...).
 *
 * Es un "record": una forma abreviada de Java moderno para declarar una clase
 * que solo almacena datos (sin logica), sin tener que escribir a mano el
 * constructor ni los metodos para leer cada campo.
 */
public record BackupResult(Path zipFile, long sizeInBytes, long durationMillis) {
}