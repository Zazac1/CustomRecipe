# 1.4.7-1.19.2-(Forge)

## Custom Recipe — Minecraft 1.19.2 (Forge)

This release brings Custom Recipe 1.4.7 to Minecraft 1.19.2 on Forge, including automatic Global Library recipes for existing and newly created worlds.

## Added

- **Forge for Minecraft 1.19.2** — create and manage custom crafting recipes in singleplayer and on dedicated servers.
- **All Worlds recipes** — switch a Global Library recipe from `Library` to `All Worlds` to add it automatically to every world.
- **Automatic setup for new worlds** — recipes marked `All Worlds` are added when a world is first opened or created.

## Improved

- Switching a recipe back to `Library` removes only its automatically added copies.
- Manually added recipes remain independent of the global toggle.
- World-specific edits remain local instead of being overwritten by the Global Library.

## Fixed

- The item search in Create Recipes accepts the first left click and keeps typing active as results refresh.
- Menu buttons display their backgrounds correctly at different heights.
- Default Recipes loads its recipe list and material previews in the dedicated-server editor.

## Compatibility / servers

- Requires Minecraft **1.19.2**, **Forge 43.5.2 or newer for this Minecraft version**, and **Java 17**.
- Requires **Cloth Config API 8.3.134 or newer**. JEI and REI integrations are optional.
- Install Custom Recipe on the server and on the clients. Server operators open the editor with `/customrecipe_server`.

## Before updating

- Back up recipes and configurations before moving them to a different Minecraft version or mod loader.
- Existing Global Library recipes remain in `Library` mode until explicitly switched to `All Worlds`.
