# Modrinth release - Custom Recipe 1.3.5 Beta NeoForge

## Version type

Beta

## Version number

1.3.5-1.21.1-(NeoForge)-beta

## Version subtitle

v1.3.5 Beta for Minecraft 1.21.1 (NeoForge)

## Loader

NeoForge

## Game version

1.21.1

## File

`build/libs/customrecipe-1.3.5-beta+1.21.1.jar`

## Version changelog

### NeoForge 1.21.1 beta

- Added beta support for Minecraft 1.21.1 using NeoForge.
- Adapted the Custom Recipe editor, Global Library, per-world configurations, and backup import/export flow for NeoForge.
- Existing Custom Recipe configurations and recipe IDs remain supported.

### Server recipe management

- Adapted `/customrecipe` so server operators edit the world currently loaded by the server.
- Global Library recipes can be staged in the server editor, then added selectively to that server world.
- Disabled recipes are removed from crafting, the recipe book, and synchronized recipe catalogs used by recipe viewers.
- Fixed disabled recipes still appearing as craftable in REI after saving a server configuration.

### Recipe browsing

- The Default Recipes browser includes vanilla recipes and recipes supplied by installed mods.
- Recipe search supports result and ingredient matching, including tagged ingredients.

### Interface

- Adapted the in-game settings screen, editor navigation, and explicit Save workflow for NeoForge.
- Import confirmations show the selected configuration and recipe counts before changes are applied.
- Fixed confirmation panels and editor overlays rendering behind the background blur.
- Fixed duplicate save confirmations while editing a server configuration.

### Beta testing

- This is a beta release. Please back up configurations and report any remaining client, dedicated-server, or recipe-viewer issues.
