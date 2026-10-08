# Modrinth release - Custom Recipe 1.3.7 NeoForge

## Version type

Release

## Version number

1.3.7-1.21.1-(NeoForge)

## Version subtitle

v1.3.7 for Minecraft 1.21.1 (NeoForge)

## Loader

NeoForge

## Game version

1.21.1

## File

`build/libs/customrecipe-1.3.7+1.21.1.jar`

## Version changelog

### Recipe browser and previews

- Reworked Default Recipes into a two-panel browser: select a recipe on the left and inspect its crafting preview directly on the right.
- The inline preview shows the crafting grid, output, shaped or shapeless format, and interchangeable material variants without leaving the browser.
- Recipe searches and filters reuse cached metadata and update visible rows without repeatedly scanning recipe resources.

### Recipe editor polish

- Improved item-picker scrolling so using the scrollbar never selects the item beneath it.
- Added 12 px scrollbars with click-to-position and thumb dragging for the item picker, shapeless ingredients, and recipe browser.
- Fixed the Recipe Builder search clear control and aligned editor labels with the search field.
- Increased Custom Recipe library rows to 22 px, centered icons, and removed empty gaps between rows.
- Improved action spacing and moved a world's Enable All and Disable All controls into the header, giving the recipe table more space.

### Commands and compatibility

- Adapted the local singleplayer editor command as `/customrecipe_solo`; it is unavailable in multiplayer.
- Adapted the dedicated-server editor command as `/customrecipe_server`; it requires operator permission and is unavailable in singleplayer.
- Fixed Default Recipes panels, previews, icons, and text rendering behind the background blur.
