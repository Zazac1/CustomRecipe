# Modrinth release - Custom Recipe 1.3.4

## Version type

Release

## Version number

1.3.4

## Version subtitle

v1.3.4 for Minecraft 1.21.8

## Loader

Fabric

## Game version

1.21.8

## File

`build/libs/customrecipe-1.3.4+1.21.8.jar`

## Version changelog

### Minecraft 1.21.8 support

- Added Custom Recipe 1.3.4 support for Minecraft 1.21.8.
- Preserved the complete RecipesCreator 1.3.4 feature set, including local and server recipe management.

### Default Recipes filters

- Changed the home-screen entry from Vanilla Recipes to Default Recipes.
- Renamed the existing recipe-state filter to Status.
- Added a Show filter to switch between All, Modded, and Vanilla recipes.
- Applied Modded and Vanilla filters to both local and server recipe catalogs.

### Compatibility

- Added a safe Global Library recipe migration so templates cannot retain enabled or server-published states.
- Refreshed REI recipe displays after a recipe reload when REI is installed.
