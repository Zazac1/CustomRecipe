# 1.3.9-1.21.1-(Fabric)

## Custom Recipe — Minecraft 1.21.1 (Fabric)

This beta update lets recipes require complete ingredient stacks instead of limiting every crafting slot to one item.

## Added

- **Ingredient quantities** — set any shaped or shapeless recipe ingredient to require between 1 and 64 items.
- **Editable ingredient counts** — click an ingredient quantity in the recipe builder to enter the exact amount needed for that slot or list entry.

## Improved

- Recipe previews now display the required quantity for every ingredient.
- Crafting consumes the configured amount from each ingredient stack.

## Fixed

- Selecting a custom recipe from the recipe book now fills each crafting slot with its complete required stack when enough items are available.
- Entering or editing an ingredient quantity no longer causes an error when the field is temporarily empty.
- The built-in recipes for the elytra, enchanted golden apple, experience bottle, heavy core, and totem of undying now load correctly on Minecraft 1.21.1.

## Compatibility / servers

- Requires Minecraft 1.21.1 with Fabric, Fabric API, and Mod Menu.
- Existing recipes and configurations remain compatible; ingredients without a saved quantity continue to require one item.

## Before updating

- Ingredient quantities and the Custom Items library are beta features. Back up the Custom Recipe configuration before testing them on an important world or server.
