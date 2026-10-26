# Release readiness — Forge 1.18.2

Release: **1.4.7-1.18.2-(Forge)**. Date: **October 26, 2026**.

## Manual acceptance

The project owner explicitly confirmed this Minecraft/loader port as tested and authorized its GitHub push and Modrinth publication in this conversation. This human acceptance is the release-readiness decision; it is not reported as an automated or agent-observed game test.

## Historical automation

The shared workspace Full run recorded 238 PASS, 0 FAIL, and 0 WARN across 17 variants on October 26, 2026. That report predates later port corrections and is retained as historical evidence.

## Publication preparation

The current source is assembled into the distributable without running the paused test suites. The packaged metadata, Minecraft/loader compatibility, resources, Java class versions, mixin configuration, and exclusion of development-only validation classes are inspected before upload.

Git history starts from the existing `forge/1.20.1` branch. No local worlds, server operator lists, account caches, logs, credentials, or private test configurations belong in the published source or artifact.

## Cloth Config requirement

Development uses Cloth Config 6.5.133. The release metadata accepts Cloth Config 6.5.102 or newer so Modrinth can resolve the published Forge 1.18.2 dependency. The editor does not call the Cloth Config API; its configuration entry opens Custom Recipe's own screen. This is a dependency-metadata adjustment, with no gameplay-code change. No additional runtime test with 6.5.102 is claimed.

## Scope

The optional JEI requirement accepts 10.2.1.1002 or newer, the Forge 1.18.2 release available on Modrinth. Static inspection confirms that this release provides the plugin annotation, plugin identifier, and runtime callback used by Custom Recipe. Development remains pinned to 10.81.0.1029. No additional JEI runtime test is claimed.

No new automated suite or Minecraft instance is started for this publication. Compilation and archive inspection are packaging evidence, separate from in-game validation.
