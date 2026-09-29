# Modrinth release - Custom Recipe 1.3.5

## Version type

Release

## Version number

1.3.5-26.1.x-(Fabric)

## Version subtitle

1.3.5-26.1.x-(Fabric)

## Loader

Fabric

## Game version

26.1, 26.1.1, 26.1.2

## File

`build/libs/customrecipe-1.3.5+26.1.x.jar`

## Version changelog

### Minecraft 26.1.x support

- Added Custom Recipe 1.3.5 support for Minecraft 26.1.x using Fabric.
- Preserved Custom Recipe's recipe editor, Global Library, and per-world recipe configurations.
- Existing 1.3.4 configurations are migrated safely and keep their saved data and preferences.

### Editor settings

- Added an in-game settings screen for Custom Recipe.
- Choose whether the editor automatically uses GUI Scale 3, which library or world opens first, and whether Default Recipes are preloaded at startup.
- Added individual reset buttons, translated tooltips, and clear on/off indicators for each setting.

### Recipe search

- Improved item search in the Recipe Builder to find items by displayed name as well as item ID.
- Improved Default Recipes search to find results and ingredients by displayed item name, item ID, recipe ID, and mod namespace.
- Searches now include every item accepted through an ingredient tag, on both local and dedicated-server recipe browsers.

### Interface

- Added a settings button to the Custom Recipe editor.
- The GUI scale is restored to the exact value selected before opening the editor.
