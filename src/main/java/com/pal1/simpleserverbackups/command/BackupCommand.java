package com.pal1.simpleserverbackups.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import com.pal1.simpleserverbackups.SimpleServerBackups;
import com.pal1.simpleserverbackups.backup.BackupInfo;
import com.pal1.simpleserverbackups.backup.BackupManager;
import com.pal1.simpleserverbackups.backup.BackupResult;
import com.pal1.simpleserverbackups.lang.LocalizedException;
import com.pal1.simpleserverbackups.lang.Messages;
import com.pal1.simpleserverbackups.restore.RestoreManager;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registra el comando /backup y sus subcomandos:
 * /backup, /backup create <nombre>, /backup list, /backup info <nombre>,
 * /backup delete <nombre>, /backup load <nombre>, /backup reload, /backup help.
 */
public class BackupCommand {

    private static final DateTimeFormatter AUTO_NAME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    private static final DateTimeFormatter DISPLAY_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final DateTimeFormatter DISPLAY_DATE_FULL_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // Mapa "comando -> clave de traduccion de su descripcion", para /backup help.
    private static final Map<String, String> COMMAND_HELP_KEYS = new LinkedHashMap<>();
    static {
        COMMAND_HELP_KEYS.put("/backup", "help.backup");
        COMMAND_HELP_KEYS.put("/backup create <nombre>", "help.create");
        COMMAND_HELP_KEYS.put("/backup list", "help.list");
        COMMAND_HELP_KEYS.put("/backup info <nombre>", "help.info");
        COMMAND_HELP_KEYS.put("/backup delete <nombre>", "help.delete");
        COMMAND_HELP_KEYS.put("/backup load <nombre>", "help.load");
        COMMAND_HELP_KEYS.put("/backup reload", "help.reload");
        COMMAND_HELP_KEYS.put("/backup help", "help.help");
    }

    private final BackupManager backupManager = new BackupManager();
    private final RestoreManager restoreManager = new RestoreManager();
    private final BackupNameSuggestionProvider nameSuggestions = new BackupNameSuggestionProvider();

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("backup")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                        .executes(this::executeAutomaticBackup)
                        .then(Commands.literal("create")
                                .then(Commands.argument("nombre", StringArgumentType.word())
                                        .executes(this::executeCreateNamed)))
                        .then(Commands.literal("list")
                                .executes(this::executeList))
                        .then(Commands.literal("info")
                                .then(Commands.argument("nombre", StringArgumentType.word())
                                        .suggests(nameSuggestions)
                                        .executes(this::executeInfo)))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("nombre", StringArgumentType.word())
                                        .suggests(nameSuggestions)
                                        .executes(this::executeDelete)))
                        .then(Commands.literal("load")
                                .then(Commands.argument("nombre", StringArgumentType.word())
                                        .suggests(nameSuggestions)
                                        .executes(this::executeLoad)))
                        .then(Commands.literal("reload")
                                .executes(this::executeReload))
                        .then(Commands.literal("help")
                                .executes(this::executeHelp))
        );
    }

    private int executeAutomaticBackup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();

        String backupName = LocalDateTime.now().format(AUTO_NAME_FORMAT);

        return runBackup(source, server, backupName);
    }

    private int executeCreateNamed(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        String backupName = StringArgumentType.getString(context, "nombre");

        return runBackup(source, server, backupName);
    }

    private int runBackup(CommandSourceStack source, MinecraftServer server, String backupName) {
        sendMessage(source, Messages.get("msg.create.creating", backupName), ChatFormatting.YELLOW, true);
        sendMessage(source, Messages.get("msg.create.saving"), ChatFormatting.GRAY, true);

        try {
            BackupResult result = backupManager.createBackup(server, backupName);

            String sizeText = formatSize(result.sizeInBytes());
            String timeText = String.format("%.1f s", result.durationMillis() / 1000.0);

            sendMessage(source, Messages.get("msg.create.done", backupName), ChatFormatting.GREEN, true);
            sendMessage(source, Messages.get("msg.create.stats", sizeText, timeText), ChatFormatting.AQUA, true);

            return 1;
        } catch (LocalizedException e) {
            source.sendFailure(Component.literal(Messages.get(e.getKey(), e.getArgs())));
            return 0;
        } catch (Exception e) {
            SimpleServerBackups.LOGGER.error("Error al crear el backup '{}'", backupName, e);
            source.sendFailure(Component.literal(Messages.get("error.create.failed", e.getMessage())));
            return 0;
        }
    }

    private int executeList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            List<BackupInfo> backups = backupManager.listBackups();

            if (backups.isEmpty()) {
                sendMessage(source, Messages.get("msg.list.empty"), ChatFormatting.GRAY, false);
                return 1;
            }

            sendMessage(source, Messages.get("msg.list.header", backups.size()), ChatFormatting.GOLD, false);

            for (BackupInfo backup : backups) {
                String size = formatSize(backup.sizeInBytes());
                String date = Instant.ofEpochMilli(backup.lastModifiedMillis())
                        .atZone(ZoneId.systemDefault())
                        .format(DISPLAY_DATE_FORMAT);

                source.sendSuccess(() -> Component.literal(" - ")
                                .append(Component.literal(backup.name()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                                .append(Component.literal("  (" + size + ", " + date + ")").withStyle(ChatFormatting.GRAY)),
                        false);
            }

            return 1;
        } catch (Exception e) {
            SimpleServerBackups.LOGGER.error("Error al listar los backups", e);
            source.sendFailure(Component.literal(Messages.get("error.list.failed", e.getMessage())));
            return 0;
        }
    }

    private int executeInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String backupName = StringArgumentType.getString(context, "nombre");

        try {
            BackupInfo info = backupManager.getBackupInfo(backupName);

            String size = formatSize(info.sizeInBytes());
            String date = Instant.ofEpochMilli(info.lastModifiedMillis())
                    .atZone(ZoneId.systemDefault())
                    .format(DISPLAY_DATE_FULL_FORMAT);

            sendMessage(source, Messages.get("msg.info.header", info.name()), ChatFormatting.GOLD, false);
            sendMessage(source, Messages.get("msg.info.size", size, info.sizeInBytes()), ChatFormatting.AQUA, false);
            sendMessage(source, Messages.get("msg.info.date", date), ChatFormatting.AQUA, false);
            sendMessage(source, Messages.get("msg.info.files", info.fileCount()), ChatFormatting.AQUA, false);

            return 1;
        } catch (LocalizedException e) {
            source.sendFailure(Component.literal(Messages.get(e.getKey(), e.getArgs())));
            return 0;
        } catch (Exception e) {
            SimpleServerBackups.LOGGER.error("Error al obtener informacion del backup '{}'", backupName, e);
            source.sendFailure(Component.literal(Messages.get("error.info.failed", e.getMessage())));
            return 0;
        }
    }

    private int executeDelete(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String backupName = StringArgumentType.getString(context, "nombre");

        try {
            backupManager.deleteBackup(backupName);
            sendMessage(source, Messages.get("msg.delete.done", backupName), ChatFormatting.GREEN, true);
            return 1;
        } catch (LocalizedException e) {
            source.sendFailure(Component.literal(Messages.get(e.getKey(), e.getArgs())));
            return 0;
        } catch (Exception e) {
            SimpleServerBackups.LOGGER.error("Error al eliminar el backup '{}'", backupName, e);
            source.sendFailure(Component.literal(Messages.get("error.delete.failed", e.getMessage())));
            return 0;
        }
    }

    private int executeLoad(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        String backupName = StringArgumentType.getString(context, "nombre");

        try {
            if (!backupManager.backupExists(backupName)) {
                source.sendFailure(Component.literal(Messages.get("error.backup_not_found", backupName)));
                return 0;
            }

            restoreManager.requestRestore(backupName);

            sendMessage(source, Messages.get("msg.load.scheduled", backupName), ChatFormatting.GOLD, true);
            sendMessage(source, Messages.get("msg.load.stopping"), ChatFormatting.GOLD, true);

            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "stop");

            return 1;
        } catch (Exception e) {
            SimpleServerBackups.LOGGER.error("Error al programar la restauracion del backup '{}'", backupName, e);
            source.sendFailure(Component.literal(Messages.get("error.load.failed", e.getMessage())));
            return 0;
        }
    }

    private int executeReload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            SimpleServerBackups.getConfig().load();
            Messages.load(SimpleServerBackups.getConfig().getLanguage());
            sendMessage(source, Messages.get("msg.reload.done"), ChatFormatting.GREEN, true);
            return 1;
        } catch (Exception e) {
            SimpleServerBackups.LOGGER.error("Error al recargar la configuracion", e);
            source.sendFailure(Component.literal(Messages.get("error.reload.failed", e.getMessage())));
            return 0;
        }
    }

    private int executeHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        sendMessage(source, Messages.get("msg.help.header"), ChatFormatting.GOLD, false);

        for (Map.Entry<String, String> entry : COMMAND_HELP_KEYS.entrySet()) {
            String command = entry.getKey();
            String description = Messages.get(entry.getValue());

            source.sendSuccess(() -> Component.literal(command).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                            .append(Component.literal(" - " + description).withStyle(ChatFormatting.GRAY)),
                    false);
        }

        return 1;
    }

    private void sendMessage(CommandSourceStack source, String text, ChatFormatting color, boolean broadcastToOps) {
        source.sendSuccess(() -> Component.literal(text).withStyle(color), broadcastToOps);
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