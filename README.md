# Custom Recipe

Custom Recipe is a Fabric mod for creating, managing, and controlling crafting recipes directly in Minecraft. Build recipes with vanilla or modded items locally through ModMenu, or on a server through an operator editor.

## Supported Minecraft versions

Download the latest release matching your game version:

- **1.20.1**
- **1.21.1**
- **1.21.8**
- **1.21.11**
- **26.2**
- **26.3** *(upcoming)*

All releases require Fabric Loader and Fabric API. ModMenu is optional and provides the local configuration entry point.

## Core features

- Create **shaped** and **shapeless** custom recipes.
- Use both Minecraft and installed-mod items in ingredients and outputs.
- Enable, disable, inspect, or remove custom recipes in-game.
- Manage included built-in recipes, such as Elytra, Totem of Undying, Enchanted Golden Apple, Bottle o' Enchanting, and Heavy Core where available.
- Browse default recipes with their real crafting grid, item icons, searchable outputs/ingredients, material variants, and per-variant or full-recipe disabling.
- Mark a recipe **Known by default** to unlock it silently in every player's recipe book.
- Keep the selected custom output usable in the green recipe book, including during Shift-crafting.
- Preserve Vanilla priority for identical recipes; disable the default recipe when you intentionally want the custom replacement.

## Local and server tools

- **ModMenu / singleplayer:** create and manage local recipes from the in-game interface.
- **Dedicated server:** operators use `/customrecipe` to open the server editor, save configuration, and reload recipes automatically.
- Server configuration is validated and protected by operator permission.

For server editing, install Custom Recipe on the server and on the operator's client. Different game versions may expose a slightly different interface because of Minecraft API changes.

## 1.3.1 highlights

The 1.3.1 release completes the recipe-management rework and adds per-world management:

- A redesigned recipe creator with live item search, scrolling/batched results, reusable used-item tiles, protected slots, and a permanent Empty tile to clear mistakes safely.
- A Global Library plus independent recipes for each local world, with a searchable world selector, thumbnails, and most-recently-played-first ordering.
- Default Recipes include installed-mod recipes, status filters, tag-material previews, and interchangeable material variants.
- Exact conflict handling: identical input and output recipes can disable the matching default recipe; recipes with the same input but a different output are guided toward Known by default instead.
- Missing-mod recovery: recipes whose items no longer exist are marked corrupted and disabled until the mod is restored or the recipe is removed.
- Client and server catalogs stay separate, so differing mod lists never leak items or recipes from one side to the other.

## Compatibility notes

- The latest feature set is available in **v1.3.1**.
- Earlier supported versions retain their latest compatible feature set and fixes.
- Choose the Modrinth file that matches your exact Minecraft version.
