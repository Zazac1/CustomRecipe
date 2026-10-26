# Release readiness — Forge 1.16.5

Release: **1.4.7-1.16.5-(Forge)**. Date: **October 26, 2026**.

## Manual acceptance

After manually checking the latest fixes, the project owner explicitly confirmed that Forge 1.16.5 was good and authorized the same GitHub/Modrinth publication workflow as the other four ports. This owner acceptance is the release-readiness decision. It is not presented as an automated or agent-observed gameplay test.

The accepted build included the counted-recipe book availability and complete placement changes, the server-save registry correction, and the earlier startup, dependency-identity, and interface fixes. The owner-supplied screenshots and logs remain local evidence; private worlds, server configurations, operator lists, and account caches are not part of this repository.

## Historical automation

The workspace Full run on October 26, 2026 recorded **238 PASS, 0 FAIL, and 0 WARN** across 17 variants. It predates the later port corrections and is historical evidence. No new Full or in-game suite was run for this publication. Automatic tests remain paused at the owner's request.

## Publication preparation

The distributable is assembled without tests. Archive inspection covers expanded metadata, Minecraft/loader compatibility, icons, required mixin classes and refmaps, Java 8 class files, the counted ingredient serializer, and exclusion of local-development/validation classes and private data. These checks are packaging evidence, separate from manual acceptance.

Git history follows the existing `forge/1.20.1` branch, with this port published on `forge/1.16.5`.

## Cloth Config requirement

Development uses Cloth Config 4.17.132. Release metadata accepts **4.17.101 or newer**, matching the compatible Forge release available on Modrinth. Both versions declare the same `cloth-config` mod ID. Custom Recipe's configuration entry opens its own editor and does not call the Cloth Config API. This is a dependency-metadata adjustment with no gameplay-code change; no additional runtime test with 4.17.101 is claimed.

JEI 7.8.1.1018 remains the development dependency. JEI and REI are optional in the release metadata.

## Scope

No Minecraft instance or automated suite is launched for release preparation or Jarvis synchronization. Compilation and archive inspection do not establish a new functional PASS. The synthetic offline-session adaptation belongs only to the local ForgeGradle client and is excluded from the distributed JAR.
