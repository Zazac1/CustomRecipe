# 1.4.2-26.1.x-(Fabric)

## Custom Recipe — Minecraft 26.1.x (Fabric)

This update fixes recipe-book detection and automatic placement for recipes that use counted or saved custom-item ingredients.

## Fixed

- The recipe book now recognizes counted recipes as craftable when all required materials are present, including materials split across several inventory stacks.
- Selecting a recipe now places the correct item variants and configured quantities in the crafting grid.
- Incomplete counted recipes no longer show or provide a crafting result, whether clicked normally or with shift-click.

## Compatibility / servers

- Recipe availability and ingredient amounts are validated on the server in multiplayer.

## Before updating

- Existing recipes and configurations remain compatible.
