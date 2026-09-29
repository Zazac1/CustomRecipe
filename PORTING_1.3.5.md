# Custom Recipe 1.3.5 — Fabric porting notes

This document compares Forge 1.3.5 with Fabric 1.20.1 version 1.3.4.

## Genuinely new features

These features do not exist in Fabric 1.3.4 and should be ported as new functionality.

### Mod settings screen

- New `ModSettingsScreen` gives players three persistent choices:
  - automatically use GUI scale 3 while using Custom Recipe screens;
  - choose the Global Library or a world as the editor's initial target;
  - preload the Vanilla Recipe browser cache at Minecraft startup.
- Each setting has a reset button, translated tooltip, and clear Yes/No state.

Source files:

- `src/main/java/fr/zazac1/customrecipe/client/ModSettingsScreen.java`
- `src/main/java/fr/zazac1/customrecipe/ModConfig.java`
- `src/main/java/fr/zazac1/customrecipe/ConfigLoader.java`
- `src/main/java/fr/zazac1/customrecipe/client/ConfigScreen.java`
- `src/main/java/fr/zazac1/customrecipe/client/RecipeTargetSelectScreen.java`
- `src/main/resources/assets/customrecipe/lang/{en_us,en_gb,fr_fr,es_es}.json`

### Automatic GUI scale

- `ClientInit` detects Custom Recipe screens, saves the player's current scale, temporarily switches to scale 3, and restores the exact original value when leaving.
- Port the screen-detection and saved-state logic; replace the Forge client event calls with Fabric client tick/screen events.

Source file: `src/main/java/fr/zazac1/customrecipe/client/ClientInit.java`.

### Recipe preloading preference

- `VanillaRecipesScreen.preloadAtStartup` fills the local recipe cache once when the new preference is enabled.
- `ClientInit` calls it on the client tick after Minecraft is ready.

Source files:

- `src/main/java/fr/zazac1/customrecipe/client/VanillaRecipesScreen.java`
- `src/main/java/fr/zazac1/customrecipe/client/ClientInit.java`

### Better item and recipe search

- The item picker now matches display names as well as registry IDs.
- The Vanilla Recipe browser matches item display names, item IDs, recipe IDs, namespaces, and every item accepted by an ingredient tag.
- The same matching rules are applied on the dedicated server search path.

Source files:

- `src/main/java/fr/zazac1/customrecipe/client/RecipeBuilderScreen.java`
- `src/main/java/fr/zazac1/customrecipe/client/VanillaRecipesScreen.java`
- `src/main/java/fr/zazac1/customrecipe/ServerConfigNetworking.java`

## Existing feature fixed for Forge parity

Interchangeable-material previews were already present in Fabric 1.3.4. Do not present this as a new Fabric feature.

- Forge now reads resolved ingredients from the local recipe manager when a world is loaded.
- Outside a world, Forge can read recipe and tag JSON directly from installed mod archives.
- For Minecraft 1.20.1, item tags must use `data/<namespace>/tags/items/<tag>.json` — `items` is plural. This fixes the stick recipe, whose `minecraft:planks` tag previously appeared empty.

Relevant Forge methods in `src/main/java/fr/zazac1/customrecipe/client/VanillaRecipesScreen.java`:

- `findLocalRecipeDetails`
- `detailsFromLoadedRecipe`
- `ingredientChoices`
- `variantPreviews`
- `collectLocalTagItems`

Fabric 1.20.1 already contains the variant-preview design. Reuse its screen and networking structure, but verify the correct tag-resource path for the targeted Minecraft version before porting.

## Forge-only build and runtime details

Do not copy these changes to Fabric/Loom unchanged.

- `build.gradle` mixin/refmap configuration fixes Forge packaged and development mixin loading.
- `dev-mods/` dependencies provide local Balm and Waystones compatibility testing only; the directory is ignored by Git.
- `src/main/resources/customrecipe.mixins.json` declares the Forge refmap.

## Assets

- `src/main/resources/assets/customrecipe/textures/gui/icons/settings.png`
- `src/main/resources/assets/customrecipe/textures/gui/icons/reset.png`

`CustomRecipeSprites.java` exposes the reset icon for the settings UI.
