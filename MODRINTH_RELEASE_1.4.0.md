# 1.4.0-1.21.1-(Fabric)

## Custom Recipe — Minecraft 1.21.1 (Fabric)

This update adds reusable Custom Items and lets recipes require complete ingredient stacks in each crafting slot.

## Added

- **Custom Items library** — save the item held in your main hand, give it a name or identifier, and reuse it as a recipe ingredient or output without losing its custom data.
- **Custom Item administration** — server administrators can save, list, give, replace, remove, and transfer Custom Items alongside recipes.
- **Ingredient quantities** — shaped and shapeless recipes can require between 1 and 64 items in each slot or ingredient entry.
- **Editable ingredient counts** — click an ingredient quantity in the recipe builder to enter the exact amount required.

## Improved

- Custom Item names and icons are displayed throughout the recipe builder, recipe list, previews, and quick-add menus.
- Recipes can distinguish a saved special item from the ordinary version of the same item.
- Recipe previews display every required quantity, and crafting consumes the configured amounts.
- Selecting a recipe from the recipe book fills and displays the complete required stack in each crafting slot when enough items are available.
- Shaped recipes smaller than the full 3×3 grid can be placed anywhere in the crafting table while keeping their required shape.

## Fixed

- Ingredient quantities are visible in the recipe-book preview inside the crafting table.
- Ingredient quantities remain readable above item icons in recipe previews and in the recipe builder.
- The **Give** and **Delete** buttons in the Custom Items library are available whenever their actions are permitted.
- The **Give** button remains unavailable outside a world and for players without server administration permission.
- Recipe outputs using a saved Custom Item display the correct item icon.
- Editing an ingredient quantity no longer causes an error while the field is temporarily empty.
- Recipe conflicts refresh correctly after a server configuration is saved.
- Recipes combining saved Custom Items with ingredient quantities synchronize correctly without disconnecting the player.
- The built-in recipes for the elytra, enchanted golden apple, experience bottle, heavy core, and totem of undying now load correctly on Minecraft 1.21.1.

## Compatibility / servers

- Requires Minecraft 1.21.1 with Fabric, Fabric API, and Mod Menu.
- Only server administrators can send local recipes or Custom Items to a dedicated server.
- Existing recipes and configurations remain compatible; ingredients without a saved quantity continue to require one item.

## Before updating

- Back up the Custom Recipe configuration before updating an important world or server.

