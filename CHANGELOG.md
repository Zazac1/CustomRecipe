# Changelog

## 1.2.1+26.2

### Recipe management overhaul

- Added missing-mod recovery, exact conflict handling, Default Recipe filters, and installed-mod recipe browsing.
- Reworked the recipe creator with reusable items, persistent selection, protected slots, and a permanent Empty tile.

### Client and server support

- Kept client and server recipe catalogs separate and staged local recipes for explicit OP review and save.
- Added server-side validation of staged recipes for missing mods and conflicts.

### Recipe-book fixes

- Fixed disabled variants, selected custom outputs, Vanilla priority, and Shift-crafting.

## 1.1.4+26.2

### Release alignment

- Updated the release version to 1.1.4.
- All existing 26.2 features remain unchanged and validated.

## 1.1.3+26.2

### Added

- Fabric support for Minecraft 26.2.
- **Known by default** option for custom recipes, which adds them silently to every player's recipe book.
- Recipe-book grouping for custom recipes that use the same crafting grid.

### Improved

- Updated the vanilla recipe browser for 26.2 recipe formats, item components, and material tags.
- Custom recipes now reload immediately in singleplayer after saving.
- Vanilla recipes keep priority when they use the same inputs as a custom recipe.

### Fixed

- Restored custom recipe loading during 26.2 server startup.
- Fixed recipe previews for recent vanilla crafting formats.
- Restored item-search suggestions when clicking back into the creation field.
- Fixed Shift-crafting after choosing a custom recipe from the recipe book.

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
