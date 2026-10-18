# 1.4.1-26.2-(Fabric)

## Custom Recipe — Minecraft 26.2 (Fabric)

This update makes counted recipes safer and keeps ingredient amounts within each item's real stack limit.

## Improved

- Ingredient amounts respect the real stack limit of each item, including non-stackable items.

## Fixed

- Recipes that require several items in one slot no longer produce an output when that slot does not contain the full amount.
- Repeated recipe-book or shift-crafting actions can no longer create results after the required ingredients have been exhausted.

## Compatibility / servers

- Server-side checks now protect counted-ingredient recipes from incomplete crafting grids.

## Before updating

- Existing recipes and configurations remain compatible.
