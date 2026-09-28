# Modrinth release - Custom Recipe 1.3.4 Forge

## Version type

Release

## Version number

1.3.4+1.20.1-forge

## Version subtitle

v1.3.4 for Minecraft 1.20.1 (Forge)

## Loader

Forge

## Game version

1.20.1

## File

`build/libs/customrecipe-1.3.4+1.20.1.jar`

## Dependencies

- Required: Cloth Config API (Forge) 11.1.136 or newer.
- Optional: Just Enough Items (JEI) 15.20.0 or newer.
- Optional: Roughly Enough Items (REI).

## Version changelog

### Forge 1.20.1 port

- Added Forge 47.4.10 support for Minecraft 1.20.1 and Java 17.
- Preserved the complete Custom Recipe editor: Global Library, per-world configurations, imports, previews, filters, Quick Add, and recipe creation and editing.
- Preserved server configuration, recipe synchronization, recipe IDs, save format, and existing world data.

### In-game configuration

- Added Cloth Config integration to the Forge Mods screen.
- Opening Custom Recipe configuration now opens the full Custom Recipe editor directly.
- Added a Custom Recipe action to the in-game pause menu for local-world management.

### Recipe browser and integrations

- Added JEI compatibility while retaining REI compatibility.
- Improved Vanilla Recipe discovery, including installed-mod recipes and all supported crafting-recipe variants.
- Added progressive loading feedback and local recipe caching in the Vanilla Recipe browser.
- Fixed disabling Vanilla crafting recipes in singleplayer and on dedicated servers.

### Server support

- Added Forge networking for server configuration, validation, saving, and recipe updates.
- Improved the local test server workflow for offline development and console commands.
