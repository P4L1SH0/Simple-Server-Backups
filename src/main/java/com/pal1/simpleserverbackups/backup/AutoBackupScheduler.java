package com.pal1.simpleserverbackups.backup;

import com.pal1.simpleserverbackups.SimpleServerBackups;
import com.pal1.simpleserverbackups.lang.Messages;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AutoBackupScheduler {

    private static final int TICKS_PER_SECOND = 20;

    private static final DateTimeFormatter AUTO_NAME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    private final BackupManager backupManager = new BackupManager();
    private long ticksSinceLastBackup = 0;

    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerTick(MinecraftServer server) {
        int intervalMinutes = SimpleServerBackups.getConfig().getAutoBackupIntervalMinutes();

        if (intervalMinutes <= 0) {
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
        CommandSourceStack consoleSource = server.createCommandSourceStack();

        consoleSource.sendSuccess(() -> Component.literal(
                Messages.get("msg.auto.creating", backupName)).withStyle(ChatFormatting.YELLOW), true);

        try {
            BackupResult result = backupManager.createBackup(server, backupName);
            String sizeText = formatSize(result.sizeInBytes());
            String timeText = String.format("%.1f s", result.durationMillis() / 1000.0);

            consoleSource.sendSuccess(() -> Component.literal(
                            Messages.get("msg.auto.done", backupName, sizeText, timeText))
                    .withStyle(ChatFormatting.GREEN), true);
        } catch (Exception e) {
            SimpleServerBackups.LOGGER.error("Error al crear el backup automatico '{}'", backupName, e);
        }
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