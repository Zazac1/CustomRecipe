# Changelog

## 1.1.3+26.2

### Added

- Fabric support for Minecraft 26.2.
- **Known by default** option for custom recipes, which adds them silently to every player's recipe book.
- Recipe-book grouping for custom recipes that use the same crafting grid.

### Improved

- Updated the vanilla recipe browser for 26.2 recipe formats, item components, and material tags.
- Custom recipes now reload immediately in singleplayer after saving.

### Fixed

- Restored custom recipe loading during 26.2 server startup.
- Fixed recipe previews for recent vanilla crafting formats.
- Restored item-search suggestions when clicking back into the creation field.

## 1.1.2+1.21.11

### Changed

- Recipes created through ModMenu are now drafts until an OP explicitly adds them to a server.

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
