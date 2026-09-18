# Custom Recipes

Custom Recipes is a Fabric mod for Minecraft **1.21.11**. Create shaped or shapeless crafting recipes in game, keep reusable recipes in a global library, and let server operators control custom and vanilla crafting recipes without editing datapacks.

## Highlights

- Visual shaped and shapeless recipe builder for vanilla and modded items.
- A **Global Library** plus completely isolated per-world recipe configurations.
- Persistent custom recipes, with stable IDs and enable/disable state.
- **Known by default** recipes are silently added to every player's recipe book.
- Custom recipes with identical inputs share one recipe-book group.
- **Quick Add** with five optional ready-made recipes and independently stored custom shortcuts.
- Vanilla and modded crafting recipe browser per world: search by output, ingredient, or recipe ID; scroll through results; preview the exact 3x3 crafting layout.
- Material variants: for recipes using tags such as planks or stone, preview each usable material and disable one material variant or the entire recipe.
- Same vanilla recipe controls in ModMenu/local singleplayer and the OP server editor.
- OP-only server configuration command with permission-checked client/server networking.

## Build and installation

Requirements: Java 21, Fabric Loader, Fabric API. ModMenu is optional but recommended.

```powershell
.\gradlew.bat build
```

Copy the JAR from `build\libs\` to the Fabric `mods` folder.

For development:

```powershell
.\gradlew.bat runClient
```

`run-local-test.ps1` builds the project, starts a local test server and client, and stops the previous test-server process before starting a new one.

## Local / ModMenu usage

Open **ModMenu -> Custom Recipes**.

Choose the target before opening a sub-menu:

- **Global Library** stores reusable recipe templates. Library recipes are not applied to any world.
- A **world** owns its own custom recipes, built-ins, disabled vanilla recipes, and disabled material variants.

The world selector scans local saves directly and uses their `level.dat` metadata and `icon.png` thumbnail. When Custom Recipes is opened from the pause menu, the current world is selected automatically. The pause-menu Custom Recipes icon opens the same editor.

- **Library**: inspect, enable, disable, or delete custom recipes for the selected target. In a world, **Add From Library** copies a library recipe; the new copy is independent.
- **Quick Add**: use the plus button above the shortcut column to enter shortcut-edit mode. Add ready-made or saved recipes as shortcuts; clicking one shows its preview and can add an independent copy to the selected target.
- **Create a Recipe**: create a shaped or shapeless recipe. From a world, it can save to that world and optionally to the Global Library.
- **Vanilla Crafting Recipes**: search vanilla crafting recipes, scroll the results, and click a name to open its preview. This is available only for a world target.

In a recipe preview, interchangeable ingredients appear in a compact icon grid:

- Green icon: the material variant is enabled.
- Red icon: the material variant is disabled.
- White corners: currently selected preview material.
- **Disable this variant** blocks crafts that use the selected interchangeable material.
- **Disable all variants** blocks the complete recipe.

Click **Save** from the home screen, Library, or Vanilla Recipes to store local settings in `config/customrecipe.json`; Custom Recipes reloads the active world's recipes immediately. Closing a screen with unsaved changes asks whether to save, discard, or cancel.

## Server administration

Install the mod on the dedicated server and on the operator's client. An operator can run:

```mcfunction
/customrecipe
```

The server sends its authoritative configuration to that operator only. The editor supports custom recipes, built-ins, vanilla crafting recipes, material variants, and manual JSON editing. Click **Save** to send the full configuration back to the server; it is written to the server `config/customrecipe.json` and recipes are reloaded.

The server validates operator permission and configuration size before accepting a save.

### Manual Edit

**Manual Edit** is an advanced JSON editor. It is useful for direct configuration edits, but invalid or incompatible JSON can remove settings. Check the JSON before applying it.

Relevant configuration fields:

```json
{
  "disabled_recipes": ["minecraft:torch"],
  "disabled_recipe_variants": [
    {
      "recipe_id": "minecraft:chest",
      "material_id": "minecraft:oak_planks"
    }
  ]
}
```

`disabled_recipes` disables the full recipe. `disabled_recipe_variants` disables only the selected interchangeable material for that recipe. Several variant rules can be stored for the same recipe.

In schema version 1, these recipe fields live inside `global_library` or a `world_configs` entry. `world_configs` is keyed by a stable save-folder ID; `world_names` is display metadata only.

## Quick Add recipes

These are optional shortcuts only. They are not inserted into a world until the player selects one and clicks **Add Recipe**.

| Result | Pattern | Ingredients |
|---|---|---|
| Totem of Undying | `_E_ / GGG / _G_` | Emerald + Gold Block |
| Enchanted Golden Apple | `GGG / GAG / GGG` | Gold Block + Apple |
| Elytra | `_S_ / MFM / M_M` | String + Phantom Membrane + Feather |
| Bottle o' Enchanting | `_L_ / EBE / _L_` | Lapis Lazuli + Emerald + Glass Bottle |
| Heavy Core | `_N_ / NBN / _N_` | Netherite Ingot + Breeze Rod |

## Compatibility

| Component | Version |
|---|---|
| Minecraft | 1.21.11 |
| Java | 21+ |
| Fabric Loader | 0.19.3+ |
| Fabric API | 0.141.4+1.21.11 |
| ModMenu | 17.0.0 (optional) |

## License and links

- License: MIT. See [LICENSE](LICENSE).
- Issues: https://github.com/Zazac1/CustomRecipe/issues
- Source: https://github.com/Zazac1/CustomRecipe
