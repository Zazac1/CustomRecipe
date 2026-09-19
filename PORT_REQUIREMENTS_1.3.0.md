# Custom Recipes 1.3.0 port requirements — Minecraft 26.2

Status: implementation complete for `1.3.0+26.2`; release authorized after build,
client/server startup, and local user verification. The regression matrix remains
as a manual follow-up record and must not be marked without an in-game test.

## Baseline comparison

| Area | 26.2 current | 1.21.11 1.3.0 target |
|---|---|---|
| Configuration | One shared flat configuration | Global Library plus isolated configurations per local world |
| Main editor | My Recipes / Built-in Recipes / Create / Default Recipes | Target selector / Library / Create Recipes / Vanilla Recipes / Save |
| Built-ins | Separate Built-in Recipes screen | Optional Quick Add shortcuts; never added by default |
| Local entry point | ModMenu only | ModMenu and a pause-menu button locked to the current world |
| UI assets | Existing 26.2 UI | Translations, target badges, world globe, Save, Quick Add, and editor sprites |
| Security | OP server editor | Same OP restriction plus remote-safe local helper and staged sub-menu saves |

## Functional requirements

### 1. Target and configuration model

- Add `GlobalRecipeTarget`, `WorldRecipeTarget`, `WorldRecipeConfig`, and stable world-ID assignment.
- Introduce schema 1 in `ModConfig`: `global_library`, `world_configs`, `world_names`, editor context, and per-world first-tip state.
- Migrate every legacy list (`custom_recipes`, built-in state, disabled recipes, and variant rules) exactly once into the intended target. Preserve legacy fields while old config files transition.
- A Global Library is reusable storage only; it must never apply recipes at runtime and must not expose Vanilla Recipe controls.
- Copying from Library to a world must create an independent recipe, not a linked reference.

### 2. World UX

- Scan local saves without opening an extra level session; show `level.dat` metadata, `icon.png`, filtering, selection, and scrolling.
- Add a current-target badge to all target-aware screens.
- Add the pause-menu button only in integrated singleplayer. It opens a target-locked editor for the loaded world.
- Send the first-world chat tip only once per physical world instance at day 0. Recreating a save with the same name must show the tip again.
- The clickable tip uses a client-only helper that must return without opening anything on remote multiplayer.

### 3. RecipesCreator and Quick Add

- Port the five-entry home screen: Select World, Library, Create Recipes, Vanilla Recipes, and Save.
- Port the Library table, selected-recipe preview, scroll behavior, empty-state placement, and save/discard navigation.
- Move former built-in recipes into Quick Add. They are optional and do not exist in a world until **Add Recipe** is clicked.
- Support custom Quick Add snapshots. Deleting a shortcut must not delete the source recipe; adding a shortcut must create a normal editable recipe copy.
- Shortcut-edit mode must leave after one successful shortcut add or a right-click.
- Keep the top Quick Add plus separate from per-row add controls and do not highlight it as a candidate row.

### 4. Save behavior and server authority

- Local Save persists the target configuration and reloads the active integrated world.
- Save from Library or Vanilla Recipes returns to RecipesCreator; the home Save returns to the screen that opened the editor.
- In the server-managed editor, Library/Vanilla Save only stages the in-memory configuration. Only the home Save may send `SaveServerConfigPayload`, write server config, and reload recipes.
- `/customrecipe`, save, validation, Vanilla-page, and Vanilla-detail C2S payloads require `PermissionLevel.GAMEMASTERS`.
- Non-OP players must not receive an editor, server recipe data, or a way to apply configuration changes.

### 5. API-port requirements

- Keep the 26.2 package/API conventions: Java 25, `GuiGraphicsExtractor`, 26.2 screen widgets, and `CraftingMenuMixin`.
- Port behavior, not 1.21.11 imports. The 1.21.11 implementation uses Java 21 `DrawContext`, `ButtonWidget`, `WorldListWidget`, and `CraftingScreenHandlerMixin`, which are not drop-in replacements.
- Rename the remaining `fr.isaac` package and resource/mixin references to `fr.zazac1` as part of the port.
- Preserve 26.2's existing recipe-book and material-variant implementation while merging the new target model.

## Mandatory regression matrix

| Scenario | Expected result |
|---|---|
| Existing 1.2.1 config | Migrates once with no lost recipes or rules |
| Two local worlds | Changes and Quick Add lists remain isolated after restart |
| Global Library | Stores templates only; Vanilla Recipes remains unavailable |
| New world / deleted-and-recreated world | Day-0 tip appears once for each physical world instance |
| Quick Add | Built-in/custom shortcut adds an independent recipe; right-click and one add exit edit mode |
| Integrated singleplayer | Pause-menu entry stays locked to the current world; Save reloads it |
| Remote non-OP | No local editor through helper; `/customrecipe` and all config payloads are denied |
| Remote OP | Sub-menu saves stage only; home Save validates, persists, and applies |
| Recipe book | Vanilla priority, variant disabling, selected custom output, and Shift-crafting stay correct |
| Dedicated server | Starts without mixin errors and applies saved recipes after reload |

## Release record

- `gradle.properties` is `1.3.0+26.2`.
- `CHANGELOG.md` and README contain the confirmed release notes.
- Build and dedicated-server startup were completed before creating `v1.3.0-26.2`.
