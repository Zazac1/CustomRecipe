# 1.4.5-1.21.8-(Fabric)

## Custom Recipe — Minecraft 1.21.8 (Fabric)

This update makes custom items easier to manage and brings safer, more consistent recipe editing across singleplayer and servers.

## Added

- **Custom item renaming** — saved custom items can now be given a new name and identifier directly from the library.
- Recipe references are automatically updated when a custom item identifier changes.
- Invalid or already-used identifiers are rejected before saving.

## Improved

- Built-in recipe examples are now optional Quick Add templates instead of being enabled automatically.
- Recipes created from Quick Add are independent copies that can be edited or removed without changing the original template.
- Local reusable recipes and their custom items are now correctly included in the server editor draft.
- Custom item identifier conflicts are resolved while preserving the recipes that reference them.
- Server changes remain drafts until an operator explicitly presses Save.
- The server recipe editor now waits for confirmation before closing. If saving fails, the editor stays open and keeps the unsaved changes.

## Fixed

- Server save failures now report a clearer reason instead of displaying a false success.
- Fixed version-specific synchronization problems that could disconnect players, incorrectly report a failed save, or reject larger server recipe configurations.
- Custom-item recipes remain correctly synchronized and usable after a server reload.

## Compatibility / servers

- Server recipe management remains restricted to authorized operators.
- Existing custom recipes, saved items, world configurations, and global libraries remain compatible.

## Before updating

- Built-in recipe examples are no longer activated automatically. Add the templates you want from Quick Add, then save the configuration.
- No additional migration steps are required.
