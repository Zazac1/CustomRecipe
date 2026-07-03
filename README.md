# Custom Recipe

A Fabric mod for Minecraft **1.21.11** that lets you create your own **shapeless** and **shaped** crafting recipes for any vanilla or modded item — directly in-game, no resource packs needed.

## Features

- 🔧 **Visual Recipe Builder** — Pick ingredients slot by slot in a 3×3 grid with live item preview
- 🔄 **Shapeless & Shaped** — Toggle the recipe mode per recipe
- 📋 **8 Built-in Recipes** — Popular community requests, individually toggled on/off
- ✅ **Enable / Disable** — Toggle your custom recipes without deleting them
- 🔍 **Item Autocomplete** — Search any vanilla or modded item by name or ID
- ⚙️ **ModMenu GUI** — Full config accessible from the Mods screen
- 💾 **Persistent Config** — Stored in `config/customrecipe.json`, survives `/reload`

## Built-in Recipes (toggleable)

| Item | Ingredients | Count |
|---|---|---|
| Saddle | 5 Leather + 2 Iron Ingot + 2 String (shaped) | ×1 |
| Name Tag | String + Paper + Iron Ingot | ×1 |
| Elytra | 8 Phantom Membrane + 1 String (shaped) | ×1 |
| Bottle o' Enchanting | Glass Bottle + 3 Lapis + 2 Emerald | ×3 |
| Trident | 4 Prismarine Shard + 1 Prismarine Crystals (shaped) | ×1 |
| Totem of Undying | 8 Gold Ingot + 1 Emerald (shaped) | ×1 |
| Heart of the Sea | 8 Nautilus Shell + 1 Prismarine Crystals (shaped) | ×1 |
| Nether Star | 4 Soul Sand + 4 Wither Skeleton Skull + 1 Diamond (shaped) | ×1 |

## Build & Installation

```powershell
.\gradlew.bat build
```

Place the JAR from `build\libs\` into your Fabric `mods` folder.

Dev launch:

```powershell
.\gradlew.bat runClient
```

## Compatibility

| | |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | ≥ 0.19.3 |
| Fabric API | 0.141.4+1.21.11 |
| ModMenu | 17.0.0 *(optional)* |

## Usage

Open **ModMenu → Custom Recipe → ⚙** (or edit `config/customrecipe.json` directly):

- **My Recipes** — View, enable/disable, preview, or delete your custom recipes
- **Built-in Recipes** — Toggle the 8 built-in recipes; click a recipe to preview its crafting grid
- **Create a Recipe** — Visual builder: click a slot, search an item, toggle shaped/shapeless, set count

## License & Distribution

- Code: MIT (see `LICENSE` file)
- Inclusion in modpacks is allowed — please credit Zazac1

## Links

- 🐛 Issues: https://github.com/Zazac1/CustomRecipe/issues
- 💻 Source: https://github.com/Zazac1/CustomRecipe
