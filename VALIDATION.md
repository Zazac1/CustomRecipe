# Release readiness — NeoForge 26.2

Release: **1.4.7-26.2-(NeoForge)**. Date: **October 26, 2026**.

## Manual acceptance

The project owner explicitly confirmed this Minecraft/loader port as tested and authorized its GitHub push and Modrinth publication in this conversation. This human acceptance is the release-readiness decision; it is not reported as an automated or agent-observed game test.

## Historical automation

The shared workspace Full run recorded 238 PASS, 0 FAIL, and 0 WARN across 17 variants on October 26, 2026. That report predates later port corrections and is retained as historical evidence.

## Publication preparation

The current source is assembled into the distributable without running the paused test suites. The packaged metadata, Minecraft/loader compatibility, resources, Java class versions, mixin configuration, and exclusion of development-only validation classes are inspected before upload.

Git history starts from the existing `neoforge/26.3` branch. No local worlds, server operator lists, account caches, logs, credentials, or private test configurations belong in the published source or artifact.

## Scope

No new automated suite or Minecraft instance is started for this publication. Compilation and archive inspection are packaging evidence, separate from in-game validation.
