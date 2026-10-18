# 1.4.1-1.21.11-(NeoForge)

## Custom Recipe — Minecraft 1.21.11 (NeoForge)

This release adds counted ingredients and custom-item recipes, with reliable crafting and recipe-book support on servers.

## Added

- **Counted ingredients** — shaped and shapeless recipes can require more than one item in each crafting slot.
- **Custom items** — saved items keep their components and can be used as recipe ingredients or outputs.

## Improved

- Recipe previews and the recipe book show the required amount directly on each ingredient.
- Shaped recipes smaller than 3×3 can be placed anywhere in the crafting grid while keeping their shape.

## Fixed

- Enabled custom recipes now work after loading or reloading a world and appear in the recipe book.
- Taking a result repeatedly or with shift-click can no longer create items after the required ingredients have been exhausted.

## Compatibility / servers

- Counted ingredients and custom-item matching are validated by the server in multiplayer.

## Before updating

- Existing recipes and configurations remain compatible.
