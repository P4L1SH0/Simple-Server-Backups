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

public class SimpleServerBackups implements ModInitializer {
	public static final String MOD_ID = "simple-server-backups";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static ModConfig config;

	@Override
	public void onInitialize() {
		LOGGER.info("Iniciando Simple Server Backups...");

		config = new ModConfig();
		config.load();

		Messages.load(config.getLanguage());

		LOGGER.info("Configuracion cargada. Carpeta de backups: '{}'", config.getBackupsFolderName());

		new RestoreManager().applyPendingRestoreIfNeeded();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			new BackupCommand().register(dispatcher);
		});

		new AutoBackupScheduler().register();
	}

	public static ModConfig getConfig() {
		return config;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}