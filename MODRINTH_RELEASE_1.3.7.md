# 1.3.7-1.21.8-(Fabric)

## Custom Recipe — Minecraft 1.21.8 (Fabric)

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
- The solo and dedicated-server editor commands now open reliably.
- The settings menu no longer crashes when it opens.
- Opening the world selector no longer causes a client crash.

## Compatibility / servers

- Requires **Minecraft 1.21.8**.
- Requires **Fabric Loader 0.16.14 or newer**.
- Requires **Fabric API 0.136.0+1.21.8 or newer**.
- Requires **Mod Menu 15.0.1 or newer**.
- Requires **Java 21 or newer**.
- `/customrecipe_solo` is available only in singleplayer.
- `/customrecipe_server` is available only on dedicated servers and remains restricted to server operators.

## Before updating

- If you used `/customrecipe` on a dedicated server, use `/customrecipe_server` after updating.
- If you used `/customrecipe_open_local` in singleplayer, use `/customrecipe_solo` after updating.
- Existing recipes and configurations remain compatible.
