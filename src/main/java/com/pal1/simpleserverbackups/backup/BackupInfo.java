package com.pal1.simpleserverbackups.backup;

import java.nio.file.Path;

/**
 * Informacion resumida de un backup ya existente en disco,
 * usada para /backup list y /backup info.
 */
public record BackupInfo(String name, long sizeInBytes, long lastModifiedMillis, int fileCount) {
}