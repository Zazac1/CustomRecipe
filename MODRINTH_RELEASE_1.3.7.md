# Modrinth release - Custom Recipe 1.3.7

## Version type

Release

## Version number

1.3.7

## Version subtitle

v1.3.7 for Minecraft 1.21.11 (Fabric)

## Loader

Fabric

## Game version

1.21.11

## File

`build/libs/customrecipe-1.3.7+1.21.11.jar`

## Version changelog

### Recipe browser and previews

- Reworked Default Recipes into a responsive two-panel browser: select a recipe on the left and inspect its crafting preview directly on the right.
- The inline preview shows the full crafting grid, output, shaped/shapeless format, and interchangeable material variants without leaving the browser.
- Recipe searches and filter changes reuse cached metadata and rebuild only visible rows, avoiding repeated full recipe-resource scans.

### Recipe editor polish

- Fixed item-picker scrollbar hit testing so scrolling never selects the item underneath the rail.
- Added 12 px scrollbars with click-to-position and thumb dragging for the item picker, shapeless ingredients, and recipe browser.
- Fixed the Recipe Builder search clear button hover/background and aligned editor labels with search text.
- Increased Custom Recipe library rows to 22 px, centered icons, and removed blank gaps between rows.
- Improved bottom action spacing and moved a world's Enable All / Disable All controls into the header, giving the recipe table more vertical space.

### Commands and compatibility

- The local singleplayer editor command is now `/customrecipe_solo` and is unavailable in multiplayer.
- The dedicated-server editor command is now `/customrecipe_server`, requires GAMEMASTERS permission, and is unavailable in singleplayer.
