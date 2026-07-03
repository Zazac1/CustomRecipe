# Custom Recipe

A Fabric mod for Minecraft **1.21.11** that lets you create your own **shapeless** and **shaped** crafting recipes for any vanilla or modded item — directly in-game, no resource packs needed.

## Features

- 🎉 **Welcome guide** — On first launch, a guide screen explains the key features and links directly to each section
- 🔧 **Visual Recipe Builder** — Click slots in a 3×3 grid, search items by name or ID, switch shaped/shapeless, set count
- 🔄 **Shapeless & Shaped** — Toggle the recipe mode per recipe; the grid enforces slot positions for shaped
- 👁 **Crafting grid preview** — Click any recipe (built-in or custom) to expand a mini 3×3 preview of its ingredients
- 📋 **5 Built-in Recipes** — Popular community requests, individually toggleable on/off
- ✅ **Enable / Disable** — Toggle your own recipes without deleting them (green/red tint in the list)
- 🔍 **Item Autocomplete** — Search any vanilla or modded item; full ID shown as tooltip on hover; drag-select in the field
- ⚙️ **ModMenu GUI** — Full config accessible from the Mods screen (Website & Issues buttons linked)
- 💾 **Persistent Config** — Stored in `config/customrecipe.json`, reloaded on every world load

## Built-in Recipes (toggleable)

| Item | Pattern | Ingredients |
|---|---|---|
| Totem of Undying | `_E_ / GGG / _G_` | Emerald + Gold Block |
| Enchanted Golden Apple | `GGG / GAG / GGG` | Gold Block + Apple |
| Elytra | `_S_ / MFM / M_M` | String + Phantom Membrane + Feather |
| Bottle o' Enchanting | `_L_ / EBE / _L_` | Lapis Lazuli + Emerald + Glass Bottle |
| Heavy Core | `_N_ / NBN / _N_` | Netherite Ingot + Breeze Rod |

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

A **welcome screen** appears on first launch to guide you. Afterwards, open **ModMenu → Custom Recipe** to access:

- **My Recipes** — View, enable/disable, preview the crafting grid, or delete your custom recipes
- **Built-in Recipes** — Toggle the 5 pre-made recipes on/off; click a row to expand the ingredient grid
- **Create a Recipe** — Visual builder: click a slot → search an item → choose shaped/shapeless → set count → Add Recipe

The config is saved to `.minecraft/config/customrecipe.json` and takes effect on the next world load (or `/reload` on a server).

## License & Distribution

- Code: MIT (see `LICENSE` file)
- Inclusion in modpacks is allowed — please credit Zazac1

## Links

- 🐛 Issues: https://github.com/Zazac1/CustomRecipe/issues
- 💻 Source: https://github.com/Zazac1/CustomRecipe
