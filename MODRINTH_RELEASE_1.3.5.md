# Modrinth release - Custom Recipe 1.3.5

## Version type

Release

## Version number

1.3.5-1.21.1-(Fabric)

## Version subtitle

v1.3.5 for Minecraft 1.21.1 (Fabric)

## Loader

Fabric

## Game version

1.21.1

## File

`build/libs/customrecipe-1.3.5+1.21.1.jar`

## Version changelog

### Minecraft 1.21.1 support

- Adapted Custom Recipe 1.3.5 for Minecraft 1.21.1 using Fabric.
- Preserved the recipe editor, Global Library, and per-world recipe configurations.
- Existing 1.3.4 configurations migrate safely and retain saved recipes and preferences.

### Editor settings

- Adapted the in-game Custom Recipe settings screen for Minecraft 1.21.1.
- Choose whether the editor automatically uses GUI Scale 3, which library or world opens first, and whether Default Recipes are preloaded at startup.
- Added reset buttons, translated tooltips, and clear on/off indicators for each setting.

### Recipe search

- Improved Recipe Builder search to find items by displayed name as well as item ID.
- Improved Default Recipes search to find results and ingredients by displayed name, item ID, recipe ID, and mod namespace.
- Searches include every item accepted through an ingredient tag in local and dedicated-server recipe browsers.

### Interface

- Added a settings button to the Custom Recipe editor.
- The GUI scale is restored to the exact value selected before opening the editor.

### Fixes

- Fixed background labels overlapping the save confirmation popup.
