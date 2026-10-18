# 1.4.2-26.2-(Fabric)

## Custom Recipe — Minecraft 26.2 (Fabric)

This beta fixes material checks for recipes that require several items in one crafting slot.

## Fixed

- The recipe book now marks counted recipes as craftable only when the inventory contains every required item.
- A crafting result no longer appears when one or more slots contain less than the configured amount.
- Normal clicks and shift-clicks can no longer collect a counted recipe result from an incomplete crafting grid.

## Compatibility / servers

- Counted ingredient amounts are validated by the server in multiplayer.

## Before updating

- Existing recipes and configurations remain compatible.
