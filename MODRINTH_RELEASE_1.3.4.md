# Modrinth release - Custom Recipe 1.3.4

## Version type

Release

## Version number

1.3.4

## Version subtitle

v1.3.4 for Minecraft 1.20.1

## Loader

Fabric

## Game version

1.20.1

## File

`build/libs/customrecipe-1.3.4+1.20.1.jar`

## Version changelog

### Port and save compatibility

- Rebuilt the 1.20.1 Fabric version on the Custom Recipe 1.3.4 architecture.
- Older configurations are migrated safely to the active world. The pre-migration file is retained as `.legacy-backup`, and the last valid version is retained as `.previous`.
- Server-editor saves wait for a confirmation that the server wrote the file. Failed writes now remain visible and report an error instead of silently doing nothing.
- Larger legacy recipe libraries are supported during client/server configuration transfer.

### Fixed

- Fixed discovery of Vanilla and installed-mod recipes in Default Recipes for Minecraft 1.20.1.
- Fixed the legacy 1.20.1 recipe result format.
- Removed the Heavy Core/Mace quick-add entry, unavailable in Minecraft 1.20.1.
- Fixed the unsaved-changes overlay so its text and buttons remain readable.
