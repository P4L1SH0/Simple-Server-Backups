package com.pal1.simpleserverbackups.backup;

/**
 * Summarized information about a backup that already exists on disk,
 * used to display it in /backup list and /backup info.
 */
public record BackupInfo(String name, long sizeInBytes, long lastModifiedMillis, int fileCount) {
}