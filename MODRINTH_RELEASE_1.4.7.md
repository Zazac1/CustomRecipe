# 1.4.7-26.3-(NeoForge)

## Custom Recipe — Minecraft 26.3 (NeoForge)

This release brings Custom Recipe 1.4.7 to Minecraft 26.3 on NeoForge, including automatic Global Library recipes for every existing and newly created world.

## Added

- **NeoForge for Minecraft 26.3** — create and manage custom crafting recipes in singleplayer and on dedicated servers.
- **All Worlds recipes** — click a recipe's state in the Global Library to switch it from `Library` to `All Worlds` and automatically add it to every world.
- **Automatic setup for new worlds** — recipes marked `All Worlds` are added when a world is opened or created for the first time.

## Improved

- Switching a recipe back to `Library` removes only the copies that were added automatically.
- Recipes added manually remain independent and are never removed by the global toggle.
- A world-specific edit remains local instead of being overwritten or removed with the Global Library copy.

## Compatibility / servers

- Requires Minecraft **26.3**, **NeoForge 26.3.0.98 or newer**, and **Java 25**.
- Install Custom Recipe on the server and on the clients. Server operators open the editor with `/customrecipe_server`.
- Roughly Enough Items (REI) remains optional.

## Before updating

- Back up your recipes and configurations before moving them to a different Minecraft version or mod loader.
- Existing Global Library recipes remain in `Library` mode until you explicitly switch them to `All Worlds`.
