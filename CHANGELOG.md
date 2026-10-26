# 1.4.7-26.1.2-(NeoForge)

## Custom Recipe — Minecraft 26.1.2 (NeoForge)

This release brings Custom Recipe 1.4.7 to Minecraft 26.1.2 on NeoForge, including automatic Global Library recipes for existing and newly created worlds.

## Added

- **NeoForge for Minecraft 26.1.2** — create and manage custom crafting recipes in singleplayer and on dedicated servers.
- **All Worlds recipes** — switch a Global Library recipe from `Library` to `All Worlds` to add it automatically to every world.
- **Automatic setup for new worlds** — recipes marked `All Worlds` are added when a world is first opened or created.

## Improved

- Switching a recipe back to `Library` removes only its automatically added copies.
- Manually added recipes remain independent of the global toggle.
- World-specific edits remain local instead of being overwritten by the Global Library.

## Fixed

- Item selection, search controls, world selection, and recipe scrollbars use the correct mouse buttons.
- Right-clicking the recipe preview cancels the carried item without editing a recipe slot.

## Compatibility / servers

- Requires Minecraft **26.1.2**, **NeoForge 26.1.2.124 or newer for this Minecraft version**, and **Java 25**.
- Roughly Enough Items (REI) is optional.
- Install Custom Recipe on the server and on the clients. Server operators open the editor with `/customrecipe_server`.

## Before updating

- Back up recipes and configurations before moving them to a different Minecraft version or mod loader.
- Existing Global Library recipes remain in `Library` mode until explicitly switched to `All Worlds`.
