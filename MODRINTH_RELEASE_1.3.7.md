# 1.3.7-1.20.1-(Forge)

## Custom Recipe — Minecraft 1.20.1 (Forge)

This update makes browsing and editing recipes clearer and smoother, especially in larger modpacks.

## Added

- **Inline recipe preview** — select a default recipe to view its crafting grid, output, recipe type, and available material variants without leaving the browser.
- **Dedicated command names** — use `/customrecipe_solo` in singleplayer and `/customrecipe_server` on a dedicated server.

## Improved

- Recipe searches and filters stay responsive when browsing large recipe lists.
- The custom recipe library is easier to read, with clearer rows and more room for recipes.
- Recipe editor scrolling is easier to use for item suggestions and shapeless ingredients.

## Fixed

- Scrolling through the item list no longer selects an item by mistake.
- The recipe editor’s search-clear control now displays and behaves cleanly.
- Recipe editor clicks behave correctly when selecting or placing items.
- The selected material variant highlight now remains visible on every side.

## Compatibility / servers

- Requires **Minecraft Forge 47.4.10** for Minecraft 1.20.1.
- Requires **Cloth Config API (Forge) 11.1.136 or newer** on the client.
- **JEI 15.20.0.118 or newer** is optional and adds recipe-viewer integration.
- Cloth Config API and JEI are not required on a dedicated server for Custom Recipe’s server features.
- `/customrecipe_solo` is available only in singleplayer.
- `/customrecipe_server` is available only on dedicated servers and remains restricted to server operators.

## Before updating

- If you used `/customrecipe` on a dedicated server, use `/customrecipe_server` after updating.
- If you used `/customrecipe_open_local` in singleplayer, use `/customrecipe_solo` after updating.
- Existing recipes and configurations remain compatible.
