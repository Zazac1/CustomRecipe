# Development validation — NeoForge 26.1.2

The opt-in development suite runs inside Minecraft with the mod's mixins enabled. It checks recipes, crafting consumption, recipe-book placement, permissions, serialization, and Global Library behavior. These sources are compiled only with -PbasicValidation and are excluded from distributed JARs.

The suite remains paused for this publication. Do not treat compilation or archive inspection as an in-game test. The owner's manual acceptance is recorded in [VALIDATION.md](../VALIDATION.md).

When testing is resumed, preserve the development configurations and worlds first. Start the server with the basicValidation property, use the cr_basic_tests prepare and run commands, and read the generated logs/basic/results.json report. Client interaction, visible rendering, and network synchronization also require the appropriate in-game scenarios.

Final workspace-wide automated validation uses the root test-all.ps1 script in its default Full mode, followed by validation/results/latest.md. Any FAIL blocks acceptance; any WARN must be reported.
