# Changelog

## 1.2.1+1.20.1

### Recipe management overhaul

- Added **Known by default** for built-in and custom recipes, missing-mod recovery, and exact conflict handling.
- Reworked **Default Recipes** with installed-mod recipes, live search, status filters, tag previews, and material variants.
- Reworked the recipe creator with reusable items, persistent selection, protected slots, and an Empty clear tile.

### Client and server support

- Kept client and server recipe catalogs separate so different mod lists remain safe.
- Local ModMenu recipes are staged in the OP `/customrecipe` editor and are only published when the OP saves.
- Staged recipes are checked against server items and recipes for missing mods and conflicts.

### 1.20.1 fixes

- Fixed local recipe discovery for Vanilla and installed mods (`recipes/` data paths and legacy result JSON).
- Fixed interchangeable tag materials for tools and other recipes in the local browser.
- Preserved disabled variants, custom-output selection, Vanilla priority, and Shift-crafting in the green recipe book.

## 1.1.4+1.20.1

- Fixed material variants in the local ModMenu editor: vanilla item tags are now resolved from Minecraft data before joining a world.
- Fixed immediate result refresh after choosing a custom recipe from the green recipe book.

## 1.1.3+1.20.1

### Recipe book improvements

- Added the **Known by default** option to custom recipe creation and editing.
- Recipes marked as known are added silently to the recipe book.
- Custom recipes with the same inputs are grouped in the green recipe book.
- Selecting a custom output from a mixed Vanilla/custom group now changes the crafting result immediately.
- The selected custom output remains active while Shift-crafting.

### Recipe priority and fixes

- Vanilla recipes keep priority when they use the same inputs as a custom recipe.
- Disabling the Vanilla recipe allows the custom recipe to be crafted.
- Fixed the 1.20.1 client and dedicated-server startup crash introduced by the recipe-book controls.

## 1.1.2+1.20.1

### Added

- Added full Fabric support for Minecraft 1.20.1.
- Includes custom recipe creation, ModMenu configuration, `/customrecipe` server configuration, vanilla recipe controls, and material variants.

### Fixed

- Fixed vanilla recipe results and icons in the local ModMenu browser.
- Fixed local custom recipes so they load in singleplayer after saving.

## 1.1.1+1.20.1

- Ported Recipes Creator 1.1.1 to Minecraft 1.20.1 (Fabric).

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
