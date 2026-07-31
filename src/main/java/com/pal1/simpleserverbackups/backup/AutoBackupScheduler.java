package com.pal1.simpleserverbackups.backup;

import com.pal1.simpleserverbackups.SimpleServerBackups;
import com.pal1.simpleserverbackups.lang.Messages;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Controls the daily automatic backup.
 *
 * Disabled by default (auto-backup-time is empty in the configuration).
 * The administrator enables it by setting a time such as "04:00" and
 * running "/backup reload" (or restarting the server).
 *
 * Important: this never runs immediately on server startup, even if the
 * configured time has already passed for today. "Today" is always
 * considered already handled as soon as the mod initializes, so the
 * automatic backup will only fire the next time the clock actually
 * reaches the configured time while the server is running.
 */
public class AutoBackupScheduler {

    private static final DateTimeFormatter AUTO_NAME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    private final BackupManager backupManager = new BackupManager();

    // The last calendar date on which the automatic backup already ran
    // (or was skipped on purpose), so we don't trigger it more than once
    // per day, and never right at server startup.
    private LocalDate lastAutoBackupDate = LocalDate.now();

    /**
     * Hooks into the server "tick" (runs 20 times per second).
     * Called once, when the mod initializes.
     */
    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerTick(MinecraftServer server) {
        String configuredTime = SimpleServerBackups.getConfig().getAutoBackupTime();

        if (configuredTime == null || configuredTime.isBlank()) {
            // Automatic backups disabled.
            return;
        }

        LocalTime targetTime;
        try {
            targetTime = LocalTime.parse(configuredTime);
        } catch (DateTimeParseException e) {
            // Invalid format in the config (should be HH:mm) - treat as disabled.
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        boolean alreadyHandledToday = today.equals(lastAutoBackupDate);
        boolean timeReached = !now.toLocalTime().isBefore(targetTime);

        if (!alreadyHandledToday && timeReached) {
            lastAutoBackupDate = today;
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