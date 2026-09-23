# Changelog

## 1.3.4+1.21.1

### Added

- Ported the 1.3.4 feature set to Minecraft 1.21.1.
- Added the Default Recipes Show filter: All, Modded, and Vanilla.

### Changed

- Renamed the home-screen Vanilla Recipes entry to Default Recipes and the recipe-state filter to Status.

### Fixed

- Disabled crafting recipes and material variants now retain their native 1.21.1 network recipe format while being blocked during crafting.

## 1.3.2+1.21.1

### Added

- Added central full-configuration Import and Export actions below Save.
- Added import confirmation details and world-name export filenames.

### Changed

- Global Library imports now preserve Vanilla settings and related recipe state.

### Fixed

- Added safe configuration writes, recovery copies, and improved legacy migration.

## 1.1.4+1.21.1

- Added the **Known by default** option to recipe creation and editing; enabled recipes are silently added to every player's recipe book.
- Custom recipes with identical inputs now share one recipe-book group and keep the selected output during Shift-crafting.
- Fixed vanilla ingredient and material-variant previews in the local ModMenu editor for the 1.21 direct ingredient JSON format.
- Fixed recipe-book output selection: a chosen custom result is no longer replaced by the first matching Vanilla result during crafting-result refreshes.
- Added a Minecraft-JAR fallback when local recipe resources do not return any craft recipes.

## 1.1.2+1.21.1

### Changed

- Recipes created through ModMenu are now drafts until an OP explicitly adds them to a server.

## 1.1.1+1.21.1

- Ported Recipes Creator 1.1.1 to Minecraft 1.21.1 (Fabric).

## 1.1.1 - 2026-08-26

### Fixed

- Disabled crafting recipes remain visible in the server recipe browser after a restart, so they can be enabled again.

## 1.1.0 - 2026-08-26

### Added

- OP-only `/customrecipe` server editor with configuration synchronization and recipe reload.
- Vanilla crafting recipe browser with search, scrolling, and exact shaped/shapeless preview layouts.
- Per-material variant controls for tag-based crafting ingredients, including per-variant and full-recipe disable states.
- Server and ModMenu/local variant previews with icon-grid status indicators.
- `run-local-test.ps1` for local client/server testing.

### Changed

- Custom recipes now use stable IDs to prevent duplicate entries between local and server configuration.
- The main configuration action is named **Save**.
- Raw JSON editing is now named **Manual Edit** and warns about advanced configuration changes.

### Fixed

- Empty vanilla-recipe searches no longer recreate the search screen in a loop.
- Recipe previews preserve shaped recipe orientation, including 1x3 tools and 2x3 doors.
