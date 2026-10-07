# Changelog

## 1.3.7+26.3

### Fixed

- **Development-client Java runtime and GPU:** Gradle compilation, build and development server keep the project JDK. When present, only Loom's `runClient` and `runClientClean` tasks launch the game with Modrinth's Zulu 25.0.4.1 JRE (`java.exe`), retaining console crash logs. Diagnosis of the native `0xC0000005` exit found the actual difference: the dev client was assigned the integrated AMD GPU while Modrinth's `javaw.exe` used the NVIDIA RTX 3050 Ti. Windows now records the same high-performance (`GpuPreference=2`) preference for Zulu `java.exe`, so the dev client uses the RTX too. Loom's dev client also mirrors Modrinth's confirmed `-Xmx4096M` and `-XX:StackShadowPages=32` settings; the ineffective `TieredStopAtLevel=1` experiment was removed. The Gradle override remains conditional for other workstations.
- **Development-client dependency parity:** aligned the Loom run with the known-good Modrinth profile: Fabric API `0.162.0+26.3`, Mod Menu `21.0.0`, Balm `26.3.0.3`, and Shogi `26.3.0.3`. The prior run used Fabric API `0.161.0`, a Mod Menu beta, Balm `26.3.0.1`, and Shogi `26.3.0.1`; its own update checker reported all four as obsolete immediately before the native resource-loading crash. The prior Balm/Shogi JARs are retained under `run-client/mods/_disabled-backups` so Fabric cannot load duplicate mod IDs.
- **Context-specific commands:** the dedicated-server editor is now `/customrecipe_server` (instead of `/customrecipe`) and the local-world editor is `/customrecipe_solo` (instead of `/customrecipe_open_local`). The former remains registered only on dedicated servers; the latter is gated at client-command-dispatch time, so it is absent from multiplayer suggestions and cannot be invoked while connected to a remote server.
- **Minecraft 26.3 mouse-button mapping:** `MouseButtonEvent` is one-based (`1` = left, `3` = right). The editor now explicitly rejects every non-left custom action, so left-click selects/places, right-click clears the carried item only in the preview, and native recipe-panel buttons still receive their click.
- **Item-picker scrollbar hit testing:** its 12 px rail is detected before suggestion rows. The scrollbar's hover, tooltip, click and drag zones are excluded from every item-row check, preventing the item behind the rail from being highlighted, tooltiped or selected while scrolling.
- **Item-picker border safety and contrast:** suggestion icons and the thumb start one pixel inside the item-list border (`suggY() + 1`), so they cannot paint over the horizontal separator below the search field. The scrollbar track uses a contrasting blue-grey fill instead of blending into the list background.
- **Shapeless ingredient scrollbar:** shifted the complete shapeless list 8 px right to reserve a 12 px rail, replaced the former thin track and arrow controls with the same `SCROLLER_IDLE`/`SCROLLER_ACTIVE` sprites as the item picker, and added click-and-drag scrolling. Mouse-wheel scrolling remains available over both the list and its rail.
- **Search clear button:** removed the vanilla `Button` widget that rendered a black/white rectangle under the custom `reject` icon. Its hitbox is handled directly in `mouseClicked`; the icon is rendered by the editor with only a subtle tinted hover, so no unwanted blue side border or vanilla hover background remains.
- **Layout alignment:** moved the dynamic slot label (`Click a slot…`, `Slot n:`, `Result item:`) from `leftX() + 8` to `leftX() + 4`, matching the visible start of the search-field text.
- Refreshed the recipe-editor `reject` icon texture.
- **Default Recipes two-pane browser:** placed the item list in its own bordered left panel; rows show the complete item ID (for example `minecraft:acacia_boat`) beside the icon and keep the Enabled/Disabled action. The search field matches the left-panel width. Ingredient, Output, Status and Show filters return to a horizontal toolbar above a new empty bordered right panel, reserved for the selected recipe's ingredients and crafting variants. The 12 px scrollbar supports click-and-drag for long filtered result sets.
- **Inline recipe preview:** selecting a left-panel row no longer replaces the screen. The former details view now renders inside the right panel: crafting grid, output, layout type, material variants, selected-material preview, and variant/all-variant disable actions. Detail payloads are accepted by the browser screen as well as the legacy details screen.
- **Local browser responsiveness:** after the initial full local recipe scan, the complete result is retained in `startupRecipeCache`. Search, Ingredient/Output, source and status filters then operate on cached recipe metadata and rebuild only the visible row widgets; they no longer re-read and parse every recipe JSON/JAR on each interaction. Toggling Enabled/Disabled also updates the staged local state without issuing a new full scan/query.
- **Preview layout:** the recipe ID is now the sole displayed name, avoiding a second output-name label that could overflow the right panel. The shaped/shapeless format sits directly below that ID, the complete 3×3 craft diagram (inputs, arrow and output) is horizontally centered beneath it, and the material variants/actions—or the “No interchangeable material” state—occupy their own centered section below the craft diagram.
- **Preview space use:** raised only the independent right-side preview panel and filter toolbar; the left browser retains its original target-badge, result-summary, full-width search field and list spacing so those elements cannot overlap. The four wider filter buttons use their dedicated upper toolbar rather than shortening the left search field. Material variants choose up to ten centered columns from the usable preview width before creating another row, and their two action buttons remain centered below the resulting grid.
- **Toolbar clearance:** restored the right-side Ingredient/Output/Status/Show toolbar to y=32 and shortened the yellow left-side status line to the result count alone (`Found X recipes`). Removing the redundant browsing instruction prevents text from extending beneath the toolbar.
- **Default Recipes table alignment:** made the result and state buttons fill each 20 px row without the extra vertical gaps previously visible between table entries. The list scrollbar now runs from the top inner edge to the bottom inner edge of its panel and is flush with the panel's inner right border. The search field and clear button now derive from the same shared inner horizontal bounds as the table, so their left and right margins match exactly.
- **Scrollbar tracks:** standardized every scrollbar background (recipe builder item/shapeless rails, Default Recipes, custom-recipe lists and quick-add, and the target selector) to neutral `#5A5A5A`, a grey slightly darker than the standard button face. Scrollbar thumb assets and behavior are unchanged.
- **Default Recipes scrollbar edge:** shifted its 12 px thumb one pixel right and trimmed the rail background by one pixel on the left. This removes the thin track-colour sliver that was visible at the thumb's right edge while preserving the rail's right alignment to the panel frame.
- **Library table rows:** increased the compact table row to 22 px so its 16–18 px output, warning, type and known-state icons can be vertically centered. Row tinting is continuous, removing blank gaps between entries, while State/Delete controls retain their normal 18 px height and sit centered with 2 px top/bottom margins.
- **Custom-recipes action layout:** moved a world's Enable All / Disable All pair into the free header space directly above the table, aligned to its right edge. Removing that former bottom row expands the recipe table. All remaining below-table actions now share a 200×18 px width/height and a 4 px vertical gap; Save keeps its intentional 200×22 px height for the save icon and uses that same 4 px spacing.

## 1.3.4+26.3

### Added

- Added a Show filter for All, Modded, and Vanilla recipes in Default Recipes.
- Added Enable All and Disable All actions for a world's custom recipes.

### Changed

- Ported the complete 1.3.4 feature set from Minecraft 26.2 to Minecraft 26.3.
- Renamed the Default Recipes state filter to Status.
- Default Recipes now filters recipe source and status consistently in local and server editors.

### Fixed

- Fixed the server Library button opening Global Library instead of the selected world.
- Fixed library templates appearing in Default Recipes.
- Fixed stale legacy recipe state when adding or enabling recipes from Global Library.
- Result counts above 64 now clamp immediately to 64.
- Refreshed REI recipe displays after recipe reload when REI is installed.

## 1.3.2+26.3

### Added

- Added central full-configuration Import and Export actions below Save.
- Added import confirmation details and world-name export filenames.

### Changed

- Global Library imports now preserve Vanilla settings and related recipe state.

### Fixed

- Added safe configuration writes, recovery copies, and improved legacy migration.

## 1.3.1+26.3

### Changed

- Ported Custom Recipe to Minecraft **26.3**.
- Updated Fabric Loader, Fabric API, and Mod Menu dependencies for 26.3.
- Adapted recipe loading to Minecraft 26.3's registry-based recipe manager.

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
