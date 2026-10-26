# 1.4.7-1.16.5-(Forge)

## Custom Recipe — Minecraft 1.16.5 (Forge)

This release brings Custom Recipe 1.4.7 to Minecraft 1.16.5 on Forge, including automatic Global Library recipes for existing and newly created worlds.

## Added

- **Forge for Minecraft 1.16.5** — create and manage custom crafting recipes in singleplayer and on dedicated servers.
- **All Worlds recipes** — switch a Global Library recipe from `Library` to `All Worlds` to add it automatically to every world.
- **Automatic setup for new worlds** — recipes marked `All Worlds` are added when a world is first opened or created.

## Improved

- Switching a recipe back to `Library` removes only its automatically added copies.
- Manually added recipes remain independent of the global toggle.
- World-specific edits remain local instead of being overwritten by the Global Library.

## Fixed

- The recipe book checks the full required ingredient quantities before marking a recipe as craftable.
- Clicking a recipe places all required ingredients together; Shift placement respects available materials and stack limits.
- Custom ingredient item data and quantities stay consistent between the server and clients.
- Editor panels no longer cover text and item names, and menu buttons display correctly at different heights.
- Selecting the mod's configuration and saving or reloading server recipes no longer cause the reported crashes.
- The item search accepts the first left click and keeps typing active as results refresh.

## Compatibility / servers

- Requires Minecraft **1.16.5**, **Forge 36.2.42 or newer for this Minecraft version**, and **Java 8**.
- Requires **Cloth Config API 4.17.101 or newer**. JEI and REI integrations are optional.
- Install matching Custom Recipe versions on the server and clients. Server operators open the editor with `/customrecipe_server`.

## Before updating

- Back up recipes and configurations before moving them to a different Minecraft version or mod loader.
- Restart the server and clients together when replacing a development build to update ingredient synchronization.
- Existing Global Library recipes remain in `Library` mode until explicitly switched to `All Worlds`.
