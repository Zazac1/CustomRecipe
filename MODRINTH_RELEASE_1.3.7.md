# Modrinth release - Custom Recipe 1.3.7

## Version type

Release

## Version number

1.3.7-26.2-(Fabric)

## Version subtitle

1.3.7-26.2-(Fabric)

## Loader

Fabric

## Game version

26.2

## File

`build/libs/customrecipe-1.3.7+26.2.jar`

## Version changelog

### Recipe browser and previews

- Reworked Default Recipes into a responsive two-panel browser: select a recipe on the left and inspect its crafting preview directly on the right.
- The inline preview now shows the full crafting grid, output, shaped/shapeless format, and interchangeable material variants without leaving the browser.
- Recipe searches and filter changes now reuse cached metadata and rebuild only visible rows, removing the severe stutter caused by repeatedly scanning all recipes.

### Recipe editor polish

- Fixed item-picker scrollbar hit testing so scrolling never selects the item underneath the rail.
- Unified scrollbar tracks, contrast, borders, drag behavior, and alignment across the item picker, ingredient lists, recipe tables, and shortcut list.
- Fixed the search clear button's unwanted vanilla hover/background strip and aligned editor labels with search text.
- Increased Custom Recipe library rows to 22 px, centered large icons, and removed blank gaps between rows.
- Improved bottom action spacing and moved a world's Enable All / Disable All controls into the header, giving the recipe table more vertical space.

### Commands and compatibility

- The local singleplayer editor command is now `/customrecipe_solo` and is unavailable in multiplayer.
- The dedicated-server editor command is now `/customrecipe_server` and is unavailable in singleplayer.
