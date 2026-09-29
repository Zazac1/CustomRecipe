# Custom Recipe 1.3.5 — Fabric 1.21.1

**Game version:** Minecraft 1.21.1  
**Loader:** Fabric  
**Release type:** Release

## Minecraft 1.21.1 support

- Added Custom Recipe 1.3.5 support for Minecraft 1.21.1 using Fabric.
- Preserved the recipe editor, Global Library, per-world configurations, multiplayer, permissions, networking, Recipe Book integration, and Shift-crafting.
- Existing 1.3.4 configurations migrate safely and retain recipes, worlds, enabled states, and preferences.

## Editor settings

- Added the in-game Custom Recipe settings screen.
- Choose automatic GUI Scale 3, the initial Global Library or world target, and Default Recipe preloading on startup.
- Added reset buttons, translated tooltips, clear on/off indicators, and persistent settings.
- GUI Scale is restored to its exact former value when leaving Custom Recipe.

## Recipe search

- Recipe Builder search finds items by registry ID and displayed name.
- Default Recipes search finds display names, item IDs, recipe IDs, mod namespaces, and items included by ingredient tags.
- The same improved search logic is available for local and dedicated-server recipe browsers.

## Interface

- Added a settings button to the editor home screen.
- Fixed save-confirmation popup layering so background labels do not overlap the modal.

## Upload

- File: `customrecipe-1.3.5+1.21.1.jar`
- Required dependency: Fabric API
