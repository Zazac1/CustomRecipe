# Changelog

## 1.3.4+26.2

### Added

- Added a Show filter for All, Modded, and Vanilla recipes in Default Recipes.
- Added Enable All and Disable All actions for a world's custom recipes.

### Changed

- Renamed the Default Recipes state filter to Status.
- Default Recipes now filters recipe source and status consistently in local and server editors.

### Fixed

- Fixed the server Library button opening Global Library instead of the selected world.
- Fixed library templates appearing in Default Recipes.
- Fixed stale legacy recipe state when adding or enabling recipes from Global Library.
- Result counts above 64 now clamp immediately to 64.

## 1.3.2+26.2

### Added

- Added full configuration Import and Export actions below the home **Save** button.
- Added a central import confirmation with separate custom-recipe and Vanilla-setting counts.
- Added the Global Library bulk-import flow for a selected world.
- Exported backup filenames now include the selected world's name.

### Changed

- Removed duplicate Import and Export actions from Library and Vanilla Recipes submenus.
- Global Library imports now preserve Vanilla recipe settings, variants, known-by-default state, and Quick Add visibility.
- Saved configurations now record the Custom Recipe and Minecraft versions that wrote them.

### Fixed

- Added atomic config writes and recovery copies for legacy configuration migrations.
- Fixed legacy cached `server_enabled: false` values disabling local recipes after upgrading.

## 1.3.1+26.2

### Changed

- Renamed all visible mod branding to **Custom Recipe**.

## 1.3.0+26.2

### Added

- Target-based recipe editing: a reusable **Global Library** and independent, persistent configurations for every local world.
- Local world browser with thumbnails, search, scrolling, metadata, and most-recently-played-first ordering.
- Target badges, current-world pause-menu entry point, and first-day local-world editor tip.
- **Quick Add** shortcuts for built-in and custom recipe snapshots.
- Translated 1.3.0 UI text and editor textures.

### Changed

- Legacy flat configurations migrate to schema 1 while retaining custom recipes, built-in state, disabled recipes, and material-variant rules.
- Local saves, Library, and Vanilla Recipes now stage edits; only the home Save button or its Escape confirmation persists and reloads them.
- `/customrecipe` and server configuration payloads remain restricted to `GAMEMASTERS`.

### Fixed

- Global Library recipes with legacy state no longer appear disabled and copy into worlds as active recipes.
- Re-enabling an existing world recipe clears stale legacy publication state.
- The local test launcher now detects an existing world lock before starting another server.

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
