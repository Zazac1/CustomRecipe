# 1.3.7+26.3 cross-version porting changelog

This is the technical change log to apply when porting the 1.3.7 polishing
work to another Minecraft version. It records the required behaviour, geometry,
and compatibility constraints; it is not the public Modrinth release text.

## Development-client Java runtime

The client crash workaround is intentionally scoped to the Loom development
clients: `build.gradle` detects Modrinth's installed Zulu 25.0.4.1 JRE under
`%APPDATA%/ModrinthApp/meta/java_versions/.../bin/java.exe` and applies it only
to `runClient` and `runClientClean` (`JavaExec.executable`). Never put the JRE
in `JAVA_HOME`: Gradle compilation requires the project JDK. Use `java.exe`, not
`javaw.exe`, because Loom's `JavaExec` task validates its executable against the
Gradle Java toolchain; `javaw.exe` is rejected even inside the same JRE. If the
JRE path is absent, the configuration deliberately does nothing and Loom uses its
usual JDK.

On this hybrid-GPU laptop, Modrinth's working `javaw.exe` was assigned Windows
high-performance GPU preference (`GpuPreference=2`) and used the NVIDIA RTX 3050
Ti, while the newly selected Zulu `java.exe` defaulted to integrated AMD Radeon
and exited with native `0xC0000005` during resource loading. Mirror the same user
registry preference in `HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences`
for the full `java.exe` path. Verify the next dev log reports the NVIDIA device;
do not mistake this driver/GPU-selection failure for a Java or mod exception. GPU
selection alone did not eliminate a subsequent resource-loading crash: before
diagnosing Java further, bring the dev runtime dependencies in line with the
working Modrinth profile.

The working Modrinth 26.3 process uses `-Xmx4096M` and
`-XX:StackShadowPages=32`. Apply those two arguments to both Loom client runs.
Do not retain `-XX:TieredStopAtLevel=1`: it was an unverified response to an old
JVM report and did not prevent the current clean-profile `0xC0000005` crash.

For 26.3, the verified set is Fabric API `0.162.0+26.3`, Mod Menu `21.0.0`,
Balm `26.3.0.3`, and Shogi `26.3.0.3`. Keep the first two in
`gradle.properties`; replace the latter two in `run-client/mods` rather than
leaving old and new copies together (Fabric will reject duplicate mod IDs).
Archive prior JARs outside the active mod folder, for example in
`run-client/mods/_disabled-backups`.

## Recipe Builder item-picker scrollbar

First patched in `1.3.7+26.3`.

When this screen is ported to another Minecraft version, preserve all of the
following details in `RecipeBuilderScreen`:

- Keep `suggestionScrollbarX()` as a 12 px rail on the right of the suggestion
  list. Check `isOverSuggestionScrollbar(...)` **before** any suggestion-row
  hover, tooltip, click, or drag logic. The rail must never select, highlight,
  or show the tooltip of the item below it.
- Keep suggestion icons and scrollbar content one pixel inside the list frame:
  rows start at `suggY() + 1`, while the list background and its border begin
  at `suggY()`. This protects the horizontal separator below the search field.
- Use a visible track colour distinct from the suggestion-list background. The
  thumb is rendered with the shared 12×15 `SCROLLER_IDLE`/`SCROLLER_ACTIVE`
  assets and must support clicking the rail and dragging the thumb.
- For shapeless recipes, reserve the rail by offsetting the full ingredient
  list 8 px to the right. Reuse the same track, 12×15 thumb sprites and
  drag mapping; do not reintroduce the legacy 3 px track or arrow controls.

## Recipe Builder search clear control

- Do not layer a vanilla `Button` under `reject.png`: its default black/white
  hover background leaks beyond the custom icon. Render the icon yourself and
  handle its 18×18 left-click hitbox in `mouseClicked` before the text field.
- Keep the hover effect as a subtle fill only; do not draw an extra blue box
  border, which creates an unwanted vertical blue line beside the cross.

## Minecraft 26.3 input mapping

`MouseButtonEvent` uses one-based buttons in 26.3. Keep left click as `1` and
right click as `3`; ports from zero-based GLFW code must adapt these values.

## Context-specific editor commands

- Register `/customrecipe_solo` as a client command only when
  `Minecraft.getInstance().getSingleplayerServer() != null`. Keep this condition
  on the command node itself (not only in its execute callback), so the command
  is absent from multiplayer suggestions and cannot be called on a remote server.
- Register `/customrecipe_server` only when the server command-registration
  environment is `DEDICATED`, and keep its `GAMEMASTERS` permission requirement.
  Local worlds use the pause-menu editor instead; do not restore either legacy
  name (`/customrecipe_open_local` or `/customrecipe`).

## Default Recipes compact browser

The `VanillaRecipesScreen` browser intentionally displays the complete result item ID
(`recipe.result()`, such as `minecraft:acacia_boat`) in the compact row button.
Keep the state button beside the row and the search field exactly as wide as the
left list panel. Restore Ingredient, Output, Status and Show as a horizontal toolbar
above the right side, and keep an empty bordered right panel below it: it is reserved
for the selected recipe's ingredients and crafting variants. Use the shared 12×15
scrollbar sprites in the reserved rail after the state button. The rail must support
click-to-position, dragging, and mouse-wheel scrolling while preserving lazy page
loading when the scroll position nears the final visible rows.

### Preview integration and performance

- A row click must set an in-screen selected recipe, then populate the right panel;
  do not navigate to a separate details screen. Port the 3×3 grid, output, recipe
  layout text, material-variant grid, selection state, and both variant-disable
  actions from `VanillaRecipeDetailsScreen` into `VanillaRecipesScreen`.
- The clientbound `VanillaRecipeDetailsPayload` receiver must route details to
  `VanillaRecipesScreen.applyDetails(...)` when that screen is open (while keeping
  the legacy detail-screen route compatible).
- Keep the full initial local scan in `startupRecipeCache`. Subsequent local
  filters must use only `id`, `result`, and preview-slot metadata from that cache;
  never reopen every resource or mod JAR for each keypress or filter click.
- `init()` must create buttons only for `visibleRows()`. The remote browser keeps
  paginated loading; changing a staged enabled/disabled state should rebuild the
  currently visible rows rather than restarting a full server query.
- Keep the preview vertically ordered: title, recipe ID (the only name shown), then
  the shaped/shapeless layout text directly below it. Center the complete 3×3 craft
  diagram (inputs, arrow and output) horizontally under that header. Put material
  variants and their actions below the diagram; when none exist, put the “No
  interchangeable material” state in that same variants section. Do not place a
  duplicate output name, variants, or status text beside the title or over the grid.
- Keep the left browser at its original positions (badge y=3, count y=34, search
  y=52, rows/panel y=78/74), including the original full-width search field and
  its clear icon at the list panel's right edge. Only the independent right preview
  uses its compact placement (filters y=32, panel y=54); its bottom must align with
  the left panel. Let the filter toolbar use the upper span from `recipeStateX()` to
  the screen's right edge. This prevents the Ingredient/Output/Status/Show button
  labels from using vanilla marquee scrolling without shrinking the search field.
  Keep the yellow left status as only `Found X recipes`; do not restore the longer
  scroll/click instruction because it extends into the filter toolbar.
- In `VanillaRecipesScreen`, keep `recipeContentX()` / `recipeContentRight()` as the
  shared inner bounds for both search and recipe rows. Give the two row buttons the
  full `ROW` height, matching the contiguous item list. The 12 px scrollbar rail
  must start at `recipeBoxY() + 1`, end at the lower inner edge
  (`recipeBoxH() - 2`), and be flush with the panel's inner right border; do not
  restore the previous inset track or its right-side gap.
- Use `#5A5A5A` (`0xFF5A5A5A`) for every scrollbar track across the UI. It is a
  neutral grey slightly darker than the normal button face; retain the existing
  thumb sprites/colours and only change the track background.
- The Default Recipes thumb has a deliberate one-pixel optical correction: draw its
  12 px sprite one pixel farther right than the 11 px track background. The track
  must still end on the panel frame; this removes the sprite/track sliver visible at
  the right edge on the 26.3 UI scale.
- `CustomRecipesScreen` uses a 22 px contiguous table row, not 20 px: center the
  16 px item/type icons at y+3, the 14 px shaped icon at y+4, and the 18 px
  warning/known/delete icons at y+2 (the warning helper's accept sprite applies its
  own -1 px offset, so pass y+3). Fill the row tint to the full row height, but keep
  State/Delete controls at their normal 18 px height and y+2 vertical centering; do
  not reintroduce inter-row background gaps.
  Material variants should use the available
  horizontal width first—up to ten 24 px cells per row—then add rows; derive click
  hitboxes, rendered cells, action-button Y position and action-button centering
  from the same column count.

## Custom Recipes action rhythm

In `CustomRecipesScreen`, keep every standard below-table action at 200×18 px
with a 4 px vertical gap. Save is the sole exception at 200×22 px because its
16 px icon needs the additional vertical room; retain the same 4 px gap above it.
For a world, move Enable All and Disable All out of the bottom stack into the
free header strip directly above the table, aligned to the table's right edge as
two 98×18 px buttons with a 4 px separation. Recalculate `bottomReserve()` after
that move: the world table must reserve only the Library, Add Recipe, Save stack
and its leading gap (74 px), allowing the table to gain the old bulk-action row.
The library picker reserves 26 px for its Back button; the global-library editor
reserves 52 px for Add Recipe and Save. Keep these values derived from the same
button dimensions if another version changes its UI scale.
