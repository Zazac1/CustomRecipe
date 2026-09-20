# Changelog

## 1.3.2+1.21.11

### Added

- Added full configuration Import and Export actions below the home **Save** button.
- Added a central import confirmation with separate custom-recipe and Vanilla-setting counts.
- Exported backup filenames now include the selected world's name.

### Changed

- Removed duplicate Import and Export actions from Library and Vanilla Recipes submenus.
- Global Library imports now preserve Vanilla recipe settings, variants, known-by-default state, and Quick Add visibility.
- Saved configurations now record the Custom Recipe and Minecraft versions that wrote them.

### Fixed

- Added atomic config writes and recovery copies for legacy configuration migrations.
- Fixed legacy cached `server_enabled: false` values disabling local recipes after upgrading.

## 1.3.1+1.21.11

### Changed

- Renamed all visible mod branding to **Custom Recipe**.

## 1.3.0+1.21.11

### Added

- Added a target selector with **Global Library** and one independent target for every local world.
- Added per-world custom recipes, built-in recipe settings, disabled vanilla recipes, and material-variant rules.
- Added **Add From Library** to copy a global recipe into a world without linking the two copies.
- Added direct creation into a world, with an optional second save to the Global Library.
- Added a Custom Recipe icon button to the pause menu; it opens the current world's editor.
- Added persistent target badges and world thumbnails across the target-aware recipe screens.
- Added a redesigned Custom Recipe home screen with direct access to target selection, Library, recipe creation, Vanilla Recipes, and Save.
- Added **Quick Add**: five optional ready-made recipes plus independently saved custom-recipe shortcuts.
- Added explicit Save buttons, with the save icon, in the home screen, recipe library, and Vanilla Recipes browser.

### Changed

- Reworked local-world discovery to scan valid save folders directly, with save metadata, thumbnails, search, hover state, and scrolling.
- Global Library is now a template-only target: Vanilla Recipes are disabled there with an explanation.
- Moved the former Built-in Recipes list into Quick Add; these recipes are no longer added to a world by default.
- Quick Add entries are independent recipe snapshots. Adding one creates a normal editable world recipe; removing it only removes the shortcut.
- Save from Library or Vanilla Recipes now returns to Custom Recipe instead of closing the whole editor.
- Saving now reloads the active world's recipes automatically and reports the action as Custom Recipe.

### Fixed

- Fixed per-world recipe changes not applying after restarting a world.
- Fixed custom recipe enable/disable state from the in-game editor.
- Closing the main editor with unsaved changes now asks whether to save or discard them.

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
