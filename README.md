# **Simple Server Backups**
![It is a modern, clean and minimalist horizontal Minecraft banner for a Modrinth or CurseForge mod page. On the left side, against a background with a soft green gradient, is a large white mod logo showing a server with three status bars, surrounded by two curved backup arrows and a chest. On the right side, in a clean, modern white font with subtle shadows, the title reads: Simple Server Backups. Below, in a smaller font, the subtitle reads: Fast • Easy • Reliable. At the bottom, there are three small white feature badges with text and icons in a darker green that read, from left to right: Automatic Backups, Easy Restore and Fully Configurable. The green background has subtle Minecraft block patterns, and the white typography has soft shadows for readability. There is enough white space for a clean and modern design.](https://cdn.modrinth.com/data/cached_images/d04979663d6fd3bd6d52c0a2ef20501a2d5dce1a_0.webp)

Simple Server Backups is a lightweight and easy-to-use Minecraft server backup mod designed for Fabric. It allows server administrators to create, manage, and restore world backups directly from in-game commands, making it easy to protect your server data.

## Features

* Create backups instantly or with a custom name.
* View a list of all available backups.
* Check detailed information about any backup.
* Delete old backups.
* Schedule a backup restore (the server will stop and restore the selected backup on the next startup).
* Reload the mod configuration without restarting the server.
* Fully configurable through a configuration file:

  * Enable and configure automatic backups.
  * Set the maximum number of backups to keep.
  * Change the mod language.
  * Customize additional backup settings.

## Commands

* `/backup` – Creates an automatic backup using the current date and time as its name.
* `/backup create <name>` – Creates a backup with a custom name.
* `/backup list` – Lists all available backups with their size and creation date.
* `/backup info <name>` – Displays detailed information about a backup.
* `/backup delete <name>` – Deletes an existing backup.
* `/backup load <name>` – Schedules a backup restoration and shuts down the server to apply it on the next startup.
* `/backup reload` – Reloads the mod configuration.
* `/backup help` – Displays the list of available commands.

## Requirements

* Fabric Loader
* Fabric API

Perfect for small and large servers that need a simple, reliable, and configurable backup solution.
