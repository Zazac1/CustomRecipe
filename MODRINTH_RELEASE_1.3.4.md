# Modrinth release - Custom Recipe 1.3.4

## Version type

Release

## Version number

1.3.4

## Version subtitle

v1.3.4 for Minecraft 1.21.1

## Loader

Fabric

## Game version

1.21.1

## File

`build/libs/customrecipe-1.3.4+1.21.1.jar`

## Version changelog

### Default Recipes filters

- Changed the home-screen entry from Vanilla Recipes to Default Recipes.
- Renamed the existing recipe-state filter to Status.
- Added a Show filter to switch between All, Modded, and Vanilla recipes.
- The Modded and Vanilla filters now apply to both local and server recipe catalogs.

### Compatibility

- Ported Custom Recipe 1.3.4 to Minecraft 1.21.1 while retaining the 1.3.4 feature set.
- Fixed recipe synchronization for Minecraft 1.21.1: disabled crafting recipes and material variants are filtered during recipe matching, so the vanilla recipe network format remains valid for clients.
