# Changelog

## 1.2.1+1.21.11

### Added

- Added missing-mod recovery, exact recipe conflict detection, and local direct-item conflict checks.
- Added installed-mod recipes, status filters, live search, tag previews, and material variants to **Default Recipes**.
- Added reusable items, persistent picker selection, protected slots, and an Empty clear tile to the recipe creator.
- Added OP server-editor staging for local recipes, with server-side missing-mod and conflict validation before opening the editor.

### Changed

- Client and server item/recipe catalogs stay independent when their installed mod lists differ.

### Fixed

- Fixed disabled variants, custom-output selection, Vanilla priority, and Shift-crafting in the green recipe book.

## 1.1.4+1.21.11

- Fixed vanilla crafting recipes missing from the local ModMenu browser when client resources return no craft entries.
- Fixed material variants in the local ModMenu editor for 1.21 direct ingredient JSON and nested vanilla item tags.

## 1.1.3+1.21.11

### Added

- Added a **Known by default** option for custom recipes in the ModMenu/local editor and OP server editor.
- Recipes marked **Known by default** are added quietly to every player's recipe book.
- Custom recipes with identical inputs now share a green-book recipe group, so players can choose an output.

### Changed

- Vanilla recipes now take priority when they use the same inputs as a custom recipe. Disabling the Vanilla recipe allows the custom recipe to be crafted.

### Fixed

- Shift-crafting keeps the custom recipe selected from the green recipe book.

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
