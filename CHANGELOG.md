# Changelog

# 1.4.9-1.21.1-(Fabric)

## Custom Recipe — Minecraft 1.21.1 (Fabric)

This update makes alternative ingredient groups easier to reuse, adds recipes that require different items from the same group, and improves editor readability.

## Added

- **Different items in repeated slots** — an alternative group can now require every repeated recipe slot to use a different item, such as four unique saplings instead of four copies of the same sapling.
- The new rule works with both shaped and shapeless recipes and remains enforced during actual crafting.

## Improved

- Alternative groups can be selected once and placed into several recipe slots without recreating the group each time.
- Left-clicking a saved group reuses the complete group, while right-clicking expands or collapses its individual items.
- The alternative ingredient editor now uses a clear, opaque background so the previous menu never interferes with its text.

## Fixed

- Delete markers and carried items now stay above placed items and quantity labels.
- Global Library previews now show the Shaped/Shapeless and Known by default icons consistently.
- Alternative-group settings and the different-item rule are preserved after saving and reloading a recipe.

## Compatibility / servers

- Requires **Minecraft 1.21.1**, **Fabric Loader 0.17.3 or newer**, and **Java 21 or newer**.
- **Fabric API** and **Mod Menu** are required. **Roughly Enough Items (REI)** remains optional.
- Install the matching Custom Recipe version on the server and clients when using server-managed recipes.

## Before updating

- Existing recipes and configurations remain compatible.
- The different-item rule is optional and remains disabled on existing alternative groups until enabled manually.
- No migration or additional setup is required after updating.

# 1.4.8-1.21.1-(Fabric)

## Custom Recipe — Minecraft 1.21.1 (Fabric)

This update lets recipe creators accept several interchangeable items in a single ingredient slot and improves the recipe editor's readability.

## Added

- **Alternative ingredients** — choose several valid items for one ingredient slot in shaped or shapeless recipes, without creating every possible recipe combination.
- **Alternative item picker** — search available items, add all matching results, or remove alternatives individually from a compact editor.

## Improved

- Alternative sets stay folded under their main item in the recipe builder; click the stacked item to expand or collapse the group.
- Expanded groups use a scrollable two-row palette, and ingredient or result quantities can be adjusted with the mouse wheel.
- Custom Recipe menus now keep Minecraft's standard menu background visible.

## Fixed

- The unsaved-changes confirmation now stays above recipe icons and uses the correct modal background.
- Custom items in the alternative picker now show their complete Custom Recipe reference instead of an ambiguous base-item name.

## Compatibility / servers

- Requires **Minecraft 1.21.1**, **Fabric Loader 0.17.3 or newer**, and **Java 21 or newer**.
- **Fabric API** and **Mod Menu** are required. **Roughly Enough Items (REI)** remains optional.
- Install the matching Custom Recipe version on the server and clients when using server-managed recipes.

## Before updating

- Existing recipes and configurations remain compatible; ingredient slots without alternatives continue to work unchanged.
- No migration or additional setup is required after updating.

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
