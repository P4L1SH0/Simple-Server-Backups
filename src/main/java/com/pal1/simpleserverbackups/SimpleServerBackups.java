package com.pal1.simpleserverbackups;

import com.pal1.simpleserverbackups.backup.AutoBackupScheduler;
import com.pal1.simpleserverbackups.command.BackupCommand;
import com.pal1.simpleserverbackups.config.ModConfig;
import com.pal1.simpleserverbackups.lang.Messages;
import com.pal1.simpleserverbackups.restore.RestoreManager;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod entry point. Loads the configuration, applies any pending world
 * restore, registers the /backup command tree, and starts the automatic
 * backup scheduler.
 */
public class SimpleServerBackups implements ModInitializer {
	public static final String MOD_ID = "simple-server-backups";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Single instance of the mod's configuration, accessible from any
	// other class via the getConfig() method below.
	private static ModConfig config;

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Starting Simple Server Backups...");

		// Load the mod configuration (creates it with defaults if it doesn't exist yet).
		config = new ModConfig();
		config.load();

		// Load the chat messages for the language selected in the configuration.
		Messages.load(config.getLanguage());

		LOGGER.info("Configuration loaded. Backups folder: '{}'", config.getBackupsFolderName());

		// VERY IMPORTANT: this must run BEFORE Minecraft starts preparing
		// the world, so we can replace it if a restore is pending.
		new RestoreManager().applyPendingRestoreIfNeeded();

		// Register the /backup command and all its subcommands.
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			new BackupCommand().register(dispatcher);
		});

		// Periodic automatic backups (disabled by default in the configuration).
		new AutoBackupScheduler().register();
	}

	/**
	 * Gives access to the already-loaded mod configuration from any other
	 * class (commands, backup manager, etc.).
	 */
	public static ModConfig getConfig() {
		return config;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}