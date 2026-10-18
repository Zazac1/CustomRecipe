# 1.4.1-1.21.8-(Fabric)

## Custom Recipe — Minecraft 1.21.8 (Fabric)

This release adds counted ingredients and custom-item recipes while securing every crafting result on the server.

## Added

- **Counted ingredients** — shaped and shapeless recipes can require more than one item in each crafting slot.
- **Custom items** — saved items keep their components and can be used as recipe ingredients or outputs.

## Improved

- Recipe previews and the recipe book show the required amount directly on each ingredient.
- Ingredient amounts respect the real stack limit of each item, including non-stackable items.

## Fixed

- Taking a result repeatedly or with shift-click can no longer create items after the required ingredients have been exhausted.
- Shaped recipes smaller than 3×3 can be placed anywhere in the crafting grid while keeping their shape.

## Compatibility / servers

- Counted ingredients and custom-item matching are validated by the server in multiplayer.

## Before updating

- Existing recipes and configurations remain compatible.
