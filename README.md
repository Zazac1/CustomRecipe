# Custom Recipe for Minecraft 1.21.1

Custom Recipe is a Fabric mod for Minecraft **1.21.1**. Create shaped or shapeless crafting recipes in game, manage built-in recipes, and let server operators control custom and vanilla crafting recipes without editing datapacks.

## Highlights

- Visual shaped and shapeless recipe builder for vanilla and modded items.
- Persistent custom recipes, with stable IDs and enable/disable state.
- **Known by default** recipes are silently added to every player's recipe book.
- Custom recipes with identical inputs share one recipe-book group.
- Five built-in recipes with enable/disable and **Known by default** controls.
- **Default Recipes** browser: Minecraft and installed-mod recipes, live search, status filters, exact 3x3 previews, and material variants.
- Material variants: for recipes using tags such as planks or stone, preview each usable material and disable one material variant or the entire recipe.
- Missing-mod recipes stay saved as **Corrupted**, are disabled safely, and recover when the required mod returns.
- Client and server catalogs remain independent. Local recipes are staged for an OP and sent only after **Save**.

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

Open **ModMenu -> Custom Recipe**.

- **My Recipes**: inspect, enable, disable, or delete custom recipes.
- **Built-in Recipes**: toggle the included recipes.
- **Create a Recipe**: create a shaped or shapeless recipe.
- **Default Recipes**: search Minecraft and installed-mod recipes, filter Enabled/Disabled, and click a name to open its preview.

In a recipe preview, interchangeable ingredients appear in a compact icon grid:

- Green icon: the material variant is enabled.
- Red icon: the material variant is disabled.
- White corners: currently selected preview material.
- **Disable this variant** blocks crafts that use the selected interchangeable material.
- **Disable all variants** blocks the complete recipe.

Click **Save** in the main menu to store local settings in `config/customrecipe.json`.

## Server administration

Install the mod on the dedicated server and on the operator's client. An operator can run:

```mcfunction
/customrecipe
```

The server sends its authoritative configuration to that operator only. Local ModMenu recipes are shown as drafts and are validated against the server catalog; they are not sent until the OP clicks **Save**. The editor supports custom recipes, built-ins, default recipes, material variants, and manual JSON editing. Click **Save** to send the full configuration back to the server; it is written to the server `config/customrecipe.json` and recipes are reloaded.

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

## Built-in recipes

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
| Minecraft | 1.21.1 |
| Java | 21+ |
| Fabric Loader | 0.16.5+ |
| Fabric API | 0.104.0+1.21.1 |
| ModMenu | 11.0.4 (optional) |

## License and links

- License: MIT. See [LICENSE](LICENSE).
- Issues: https://github.com/Zazac1/CustomRecipe/issues
- Source: https://github.com/Zazac1/CustomRecipe
