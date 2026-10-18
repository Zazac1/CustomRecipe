# 1.3.8-1.21.1-(Fabric)

## Custom Recipe — Minecraft 1.21.1 (Fabric)

This beta update lets you save special items with all their custom data and use them in recipes like regular items.

## Added

- **Custom Items library (beta)** — save the item held in your main hand, give it a name or identifier, and reuse it as a recipe ingredient or output without losing its custom data.
- **Server Custom Items transfer** — server administrators can stage their locally saved Custom Items alongside local recipes in the server editor.
- **Custom Item commands** — administrators can save, list, give, replace, or remove saved Custom Items with `/customrecipe_item`.

## Improved

- Recipes using a special version of an ordinary item now match its complete custom data, while normal versions of that item continue to work in their usual recipes.
- Custom Item names and icons are displayed in the recipe builder, recipe list, previews, and quick-add menus.
- Server recipe and Custom Item management is restricted to administrators, including every configuration action sent to the server.

## Fixed

- The **Give** and **Delete** buttons in the Custom Items library now become available when their actions are permitted.
- The **Give** button is unavailable outside a world and for players without server administration permission.
- Recipe outputs using a saved Custom Item now display the correct item icon.
- Recipe conflicts are refreshed again when a server configuration is saved.

## Compatibility / servers

- Requires Minecraft 1.21.1 with Fabric, Fabric API, and Mod Menu.
- Custom Items and local recipes are never sent to a dedicated server by players without administration permission.
- Existing recipes and configurations remain compatible.

## Before updating

- The Custom Items library is a beta feature. Back up the Custom Recipe configuration before testing it on an important world or server.
