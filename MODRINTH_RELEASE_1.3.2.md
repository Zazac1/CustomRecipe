# Modrinth release - Custom Recipe 1.3.2

## Version type

Release

## Version number

1.3.2

## Version subtitle

v1.3.2 for Minecraft 1.21.11

## Loader

Fabric

## Game version

1.21.11

## File

`build/libs/customrecipe-1.3.2+1.21.11.jar`

## Version changelog

### Added

- Added dedicated Import Library and Export Library actions using the Windows file picker.
- Added Global Library import/export support in the dedicated-server OP editor.

### Changed

- Global Library imports merge missing recipes without deleting existing library recipes.
- Saved configurations now identify the Custom Recipe and Minecraft versions that wrote them.

### Fixed

- Added atomic config writes and automatic recovery copies (`.previous` and `.legacy-backup`).
- Improved migration of 1.20.1 and 1.21.8 saves: custom recipes, disabled recipes, variants, and known-by-default recipes are restored safely.
- Old cached `server_enabled: false` values no longer disable legacy local recipes after upgrading.