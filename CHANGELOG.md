# Changelog

## 1.2.0+1.21.8

### Added

- Added **Known by default** controls for built-in recipes, including silent recipe-book unlocks.
- Added the Default Recipes status filter: **All**, **Enabled**, and **Disabled**.
- Added the grouped recipe creator workspace, reusable used-items palette, persistent item picker, protected filled slots, and permanent Empty tile.
- Added missing-mod protection: required mods are recorded and missing recipes stay saved as **Corrupted** until their dependencies return.
- Added exact recipe conflict handling: different outputs receive Known by default advice; matching output items conflict even when their output counts differ.
- Added local ModMenu conflict detection for direct-item recipes.

### Changed

- Default Recipes now includes installed mod recipes, live search, a clear button, item-tag previews, material variants, and client-side local browsing.
- Default Recipes keeps client and server catalogs isolated: ModMenu uses client resources, while `/customrecipe` uses server resources.
- The item browser renders ten entries at a time and loads further entries in batches while scrolling.

### Fixed

- Disabled recipe variants are removed from the green recipe book after reload and no longer auto-fill the crafting grid.
- Recipe-book grouping, Vanilla priority, and Shift-crafting preserve the selected custom output.
- Local recipe scans safely handle nested Fabric module origins and load installed mod recipe archives.
- Tool and material previews correctly resolve item tags such as ingots, gems, logs, and ores.

## 1.1.4+1.21.8

- Fixed vanilla crafting recipes missing from the local ModMenu browser when client resources return no craft entries.
- Fixed material variants in the local ModMenu editor for 1.21 direct ingredient JSON and nested vanilla item tags.

## 1.1.3+1.21.8

### Added

- Added a **Known by default** option for custom recipes in the ModMenu/local editor and OP server editor.
- Recipes marked **Known by default** are added quietly to every player's recipe book.
- Custom recipes with identical inputs now share a green-book recipe group, so players can choose an output.

### Changed

- Vanilla recipes now take priority when they use the same inputs as a custom recipe. Disabling the Vanilla recipe allows the custom recipe to be crafted.

### Fixed

- Shift-crafting keeps the custom recipe selected from the green recipe book.

## 1.1.2+1.21.8

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
