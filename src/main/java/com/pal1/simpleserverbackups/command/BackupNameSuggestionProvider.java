package com.pal1.simpleserverbackups.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import com.pal1.simpleserverbackups.SimpleServerBackups;
import com.pal1.simpleserverbackups.backup.BackupInfo;
import com.pal1.simpleserverbackups.backup.BackupManager;

import net.minecraft.commands.CommandSourceStack;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Sugiere (autocompleta con Tab) los nombres de los backups que ya existen
 * en disco. Se usa en los argumentos "nombre" de /backup delete y /backup load.
 */
public class BackupNameSuggestionProvider implements SuggestionProvider<CommandSourceStack> {

    private final BackupManager backupManager = new BackupManager();

    @Override
    public CompletableFuture<Suggestions> getSuggestions(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) throws CommandSyntaxException {

        try {
            List<BackupInfo> backups = backupManager.listBackups();
            for (BackupInfo backup : backups) {
                builder.suggest(backup.name());
            }
        } catch (Exception e) {
            SimpleServerBackups.LOGGER.error("Error al generar sugerencias de nombres de backup", e);
        }

        return builder.buildFuture();
    }
}