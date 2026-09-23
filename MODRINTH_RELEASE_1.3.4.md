# Modrinth release - Custom Recipe 1.3.4

## Version type

Release

## Version number

1.3.4

## Version subtitle

v1.3.4 for Minecraft 26.2

## Loader

Fabric

## Game version

26.2

## File

`build/libs/customrecipe-1.3.4+26.2.jar`

## Version changelog

### Default Recipes filters

- Changed the home-screen entry from Vanilla Recipes to Default Recipes.
- Renamed the recipe-state filter to Status.
- Added a Show filter to switch between All, Modded, and Vanilla recipes.
- Status and source filters now apply to both local and server recipe catalogs.

### Recipe management

- Added Enable All and Disable All actions for a world's custom recipes.
- Global Library templates now remain active when copied into a world.
- Fixed the dedicated-server Library button opening Global Library instead of the selected world.

### Fixes

- Removed Custom Recipe library templates from the Default Recipes browser.
- Result-count input now clamps values above 64 to 64 immediately.
- Refreshed REI recipe displays after recipe reload when REI is installed.
