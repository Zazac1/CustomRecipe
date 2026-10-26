# Custom Recipe — Forge 1.16.5

Custom Recipe **1.4.7** for **Minecraft 1.16.5**, **Forge 36.2.42 or newer for this Minecraft version**, and **Java 8**.

Create shaped and shapeless recipes with a visual editor, custom ingredient quantities and item data, a Global Library, per-world settings, and rules for existing recipes.

## Installation

- Install Forge for Minecraft 1.16.5 and Java 8.
- Install Cloth Config API **4.17.101 or newer** for this Minecraft/loader combination.
- Put `customrecipe-1.4.7+1.16.5.jar` in the `mods` folder on the server and clients.
- JEI and REI are optional; use their Forge 1.16.5 releases.
- Restart clients and servers together when replacing a development build, so ingredient quantities and custom item data use the matching synchronization format.

## Use

- Open the editor from Custom Recipe's configuration button in the Forge mod list.
- In singleplayer, the Custom Recipe pause-menu button edits the current world.
- On dedicated servers, operators use `/customrecipe_server`.
- Switch a Global Library recipe to `All Worlds` to share it automatically with existing and new worlds.
- The recipe book checks required quantities and places a complete craft; Shift placement uses the largest affordable batch within stack limits.
- Client and server configurations are stored separately in `config/customrecipe.json`.

## Build and development

Build with **JDK 17**:

```powershell
.\gradlew.bat assemble
```

The distributable is `build/libs/customrecipe-1.4.7+1.16.5.jar`. Jabel compiles the modern source syntax to Java 8 class files.

Local Minecraft clients and servers run on **Java 8**, using separate `run/client` and `run/server` directories. Install a Java 8 JDK for Gradle toolchain discovery, or configure its location in `gradle.properties`. The shared development workspace also contains the Java 8 toolchain at the relative path already listed there.

The `localDevelopment` source set is added only to the ForgeGradle client. It uses vanilla's offline social service only for ForgeGradle's synthetic placeholder session in a non-production environment. Real account sessions retain the normal authentication path. These classes and their mixin configuration are excluded from the distributed JAR and the dedicated-server launch.

Validation sources are opt-in with `-PbasicValidation` and are always excluded from the distributed JAR. Compilation is separate from functional testing in Minecraft.

## Release information

- [Changelog](CHANGELOG.md)
- [Validation record](VALIDATION.md)
- [Custom Recipe on Modrinth](https://modrinth.com/mod/custom-recipes-mod)
- Source branch: `forge/1.16.5`, following the existing loader/Minecraft branch convention.
- License: [MIT](LICENSE).
