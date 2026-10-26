# Custom Recipe — Forge 1.19.2

Custom Recipe **1.4.7** for **Minecraft 1.19.2**, **Forge 43.5.2 or newer for this Minecraft version**, and **Java 17**.

Create shaped and shapeless crafting recipes with a visual editor, custom ingredient quantities and item data, a Global Library, per-world settings, and rules for existing recipes.

## Installation

- Install Forge for Minecraft 1.19.2 and Java 17.
- Install Cloth Config API 8.3.134 or newer for this Minecraft/loader combination.
- Put `customrecipe-1.4.7+1.19.2.jar` in the `mods` folder.
- Install Custom Recipe on both the server and the clients for multiplayer editing and synchronization.
- JEI and REI are optional.

## Use

- Open the editor from Custom Recipe's configuration button in the Forge mod list.
- In singleplayer, the Custom Recipe pause-menu button edits the current world.
- On dedicated servers, operators use `/customrecipe_server`.
- Use the Global Library's `All Worlds` state to share recipes automatically with existing and new worlds.
- Client and server configurations are stored separately in `config/customrecipe.json`.

## Build

With a JDK 17 installed, run from this directory:

```powershell
.\gradlew.bat assemble
```

The distributable is `build/libs/customrecipe-1.4.7+1.19.2.jar`.

Development clients and servers use separate `run/client` and `run/server` directories. Development-only validation sources are opt-in with `-PbasicValidation` and are excluded from distributed JARs.

## Release information

- [Changelog](CHANGELOG.md)
- [Validation record](VALIDATION.md)
- [Custom Recipe on Modrinth](https://modrinth.com/mod/custom-recipes-mod)
- Source branch: `forge/1.19.2`, following the existing loader/Minecraft branch convention.
- License: [MIT](LICENSE).
