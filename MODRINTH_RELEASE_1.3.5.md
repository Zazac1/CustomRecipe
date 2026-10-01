# Modrinth release - Custom Recipe 1.3.5 NeoForge 1.21.11

## Version type

Release

## Version number

1.3.5+1.21.11-neoforge

## Version subtitle

v1.3.5 for Minecraft 1.21.11 (NeoForge)

## Loader

NeoForge

## Game version

1.21.11

## File

`build/libs/customrecipe-1.3.5+1.21.11.jar`

## Version changelog

### NeoForge 1.21.11 support

- Added support for Minecraft 1.21.11 using NeoForge.
- Adapted the Custom Recipe editor, Global Library, per-world configurations, and backup import/export flow for NeoForge.
- Existing Custom Recipe configurations and recipe IDs remain supported.

### Server recipe management

- Adapted `/customrecipe` so server operators edit the world currently loaded by the server.
- Global Library recipes can be staged in the server editor, then added selectively to that server world.
- Disabled recipes are removed from crafting and the recipe book while remaining manageable in the editor.
- Fixed recipe-conflict warnings for recipes affected by disabled or material-filtered recipe rules.

### REI compatibility

- Preserved Roughly Enough Items (REI) compatibility for NeoForge 1.21.11.
- REI refreshes after joining a world and after server recipe changes, so it follows the enabled recipe catalogue.

### Recipe browsing

- The Default Recipes browser includes vanilla recipes and recipes supplied by installed mods.
- Recipe search supports result and ingredient matching, including tagged ingredients.

### Interface

- Adapted the in-game settings screen, editor navigation, and explicit Save workflow for NeoForge.
- Import confirmations show the selected configuration and recipe counts before changes are applied.
- Fixed confirmation panels and editor overlays rendering behind the background blur.
- Fixed duplicate save confirmations while editing a server configuration.
