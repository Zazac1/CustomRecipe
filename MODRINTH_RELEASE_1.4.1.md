# 1.4.1-1.21.1-(Fabric)

## Custom Recipe — Minecraft 1.21.1 (Fabric)

This update keeps custom recipes available in the recipe book and makes counted crafting safe in multiplayer.

## Fixed

- Custom recipes are synchronized without disconnecting players when the server refreshes its recipe list.
- Recipes that require several items in one slot no longer produce an output when that slot does not contain the full amount.
- Repeated recipe-book or shift-crafting actions can no longer create results after the required ingredients have been exhausted.

## Compatibility / servers

- Server-side checks now protect counted-ingredient recipes from incomplete crafting grids.

## Before updating

- Existing recipes and configurations remain compatible.
