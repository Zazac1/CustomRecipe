# Modrinth release - Custom Recipe 1.3.2

## Version type

Release

## Version number

1.3.2

## Version subtitle

v1.3.2 for Minecraft 26.2

## Loader

Fabric

## Game version

26.2

## File

`build/libs/customrecipe-1.3.2+26.2.jar`

## Version changelog

### Added

- Added full configuration Import and Export actions below the home Save button.
- Added a central import confirmation with separate custom-recipe and Vanilla-setting counts.
- Added the Global Library bulk-import flow for a selected world.
- Exported backup filenames now include the selected world's name.

### Changed

- Import and Export are no longer duplicated in Library and Vanilla Recipes submenus.
- Global Library imports preserve Vanilla recipe settings, variants, known-by-default state, and Quick Add visibility.
- Saved configurations identify the Custom Recipe and Minecraft versions that wrote them.

### Fixed

- Added atomic config writes and automatic recovery copies (`.previous` and `.legacy-backup`).
- Improved migration of 1.20.1 and 1.21.8 saves: custom recipes, disabled recipes, variants, and known-by-default recipes are restored safely.
- Old cached `server_enabled: false` values no longer disable legacy local recipes after upgrading.
