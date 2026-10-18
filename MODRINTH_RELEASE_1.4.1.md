# 1.4.1-1.21.10-(Fabric)

## Custom Recipe — Minecraft 1.21.10 (Fabric)

This security update makes recipes with counted ingredients safe and reliable in multiplayer.

## Fixed

- Recipes that require several items in one slot no longer produce an output when that slot does not contain the full amount.
- Repeated recipe-book or shift-crafting actions can no longer create results after the required ingredients have been exhausted.

## Compatibility / servers

- Server-side checks now protect counted-ingredient recipes from incomplete crafting grids.

## Before updating

- Existing recipes and configurations remain compatible.
