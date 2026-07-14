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
 * Suggests (Tab-completes) the names of backups that already exist on
 * disk. Used in the "name" arguments of /backup delete and /backup load.
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
            SimpleServerBackups.LOGGER.error("Error generating backup name suggestions", e);
        }

        return builder.buildFuture();
    }
}