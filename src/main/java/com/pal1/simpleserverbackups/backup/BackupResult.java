package com.pal1.simpleserverbackups.backup;

import java.nio.file.Path;

/**
 * Represents the result of a finished backup: where the file ended up,
 * how big it is, and how long it took to create. Used to build the
 * messages shown to the user ("Backup completed", size, time...).
 *
 * This is a "record": a shorthand way in modern Java to declare a class
 * that only stores data (no logic), without having to write the
 * constructor or the getters by hand.
 */
public record BackupResult(Path zipFile, long sizeInBytes, long durationMillis) {
}