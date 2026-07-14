package com.pal1.simpleserverbackups.backup;

import com.pal1.simpleserverbackups.SimpleServerBackups;
import com.pal1.simpleserverbackups.lang.Messages;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Controls the periodic automatic backups.
 *
 * Disabled by default (auto-backup-interval-minutes = 0 in the
 * configuration). The administrator enables it by setting a number of
 * minutes greater than 0 and running "/backup reload" (or restarting
 * the server).
 */
public class AutoBackupScheduler {

    private static final int TICKS_PER_SECOND = 20;

    private static final DateTimeFormatter AUTO_NAME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    private final BackupManager backupManager = new BackupManager();
    private long ticksSinceLastBackup = 0;

    /**
     * Hooks into the server "tick" (runs 20 times per second).
     * Called once, when the mod initializes.
     */
    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerTick(MinecraftServer server) {
        int intervalMinutes = SimpleServerBackups.getConfig().getAutoBackupIntervalMinutes();

        if (intervalMinutes <= 0) {
            // Automatic backups disabled: don't accumulate time.
            ticksSinceLastBackup = 0;
            return;
        }

        ticksSinceLastBackup++;

        long ticksNeeded = (long) intervalMinutes * 60 * TICKS_PER_SECOND;

        if (ticksSinceLastBackup >= ticksNeeded) {
            ticksSinceLastBackup = 0;
            runAutomaticBackup(server);
        }
    }

    private void runAutomaticBackup(MinecraftServer server) {
        String backupName = LocalDateTime.now().format(AUTO_NAME_FORMAT);

        broadcast(server, Messages.get("msg.auto.creating", backupName), ChatFormatting.YELLOW);

        try {
            BackupResult result = backupManager.createBackup(server, backupName);
            String sizeText = formatSize(result.sizeInBytes());
            String timeText = String.format("%.1f s", result.durationMillis() / 1000.0);

            broadcast(server, Messages.get("msg.auto.done", backupName, sizeText, timeText), ChatFormatting.GREEN);
        } catch (Exception e) {
            SimpleServerBackups.LOGGER.error("Error creating automatic backup '{}'", backupName, e);
        }
    }

    /**
     * Sends a message to every connected player, since automatic backups
     * are a server-wide event that everyone should be aware of.
     */
    private void broadcast(MinecraftServer server, String text, ChatFormatting color) {
        Component message = Component.literal(text).withStyle(color);
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exponent = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exponent - 1) + "B";
        return String.format("%.2f %s", bytes / Math.pow(1024, exponent), unit);
    }
}