# Modrinth release - Custom Recipe 1.3.2

## Version type

Release

## Version number

1.3.2

## Version subtitle

v1.3.2 for Minecraft 1.20.1

## Loader

Fabric

## Game version

1.20.1

## File

`build/libs/customrecipe-1.3.2+1.20.1.jar`

## Version changelog

### Added

- Added full configuration Import and Export actions and a confirmation showing recipe and Vanilla-setting counts.
- Added per-world recipe targets and a reusable Global Library.
- Exported backup filenames now include the selected world's name.

### Changed

- Global Library recipes can be copied into the active world without overwriting existing recipes.
- Saved configurations identify the Custom Recipe and Minecraft versions that wrote them.

### Fixed

- Added atomic config writes and automatic recovery copies (`.previous` and `.legacy-backup`).
- Improved migration of legacy recipes, disabled recipes, variants, and known-by-default settings.
- Old cached `server_enabled: false` values no longer disable legacy local recipes after upgrading.
