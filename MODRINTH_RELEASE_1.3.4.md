# Modrinth release - Custom Recipe 1.3.4

## Version type

Release

## Version number

1.3.4

## Version subtitle

v1.3.4 for Minecraft 26.3

## Loader

Fabric

## Game version

26.3

## File

`build/libs/customrecipe-1.3.4+26.3.jar`

## Version changelog

### 1.3.4 ported to Minecraft 26.3

- Includes the complete Custom Recipe 1.3.4 feature set from Minecraft 26.2.
- Added the Default Recipes **Show** filter: All, Modded, or Vanilla recipes.
- Added **Enable All** and **Disable All** for a world's custom recipes.
- Renamed the Default Recipes state filter to **Status**.
- Default Recipes source and status filters now apply consistently in local and server editors.

### Fixes

- The server Library button now opens the selected world's Library.
- Global Library templates no longer appear in Default Recipes.
- Adding or enabling Global Library recipes no longer retains stale legacy state.
- Result counts clamp to 64 immediately.
- REI displays refresh after a recipe reload when REI is installed.

### Compatibility

- Minecraft 26.3 / Fabric Loader 0.19.5 / Fabric API 0.161.0+26.3.
- Vanilla recipe priority, client/server synchronization, permissions, save formats, and recipe behavior are preserved from 1.3.4.
