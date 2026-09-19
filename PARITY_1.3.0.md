# 1.3.0 parity tracker: 1.21.11 -> 26.2

Rule: 26.2 exposes the same player-facing behavior and screens as 1.21.11
1.3.0. The unchecked entries remain manual regression follow-up, not claims of failure.

## Shared model and runtime

- [x] `ModConfig`, target model, world-scoped runtime, migration, independent copies.
- [ ] Verify migration and runtime behavior in a real 26.2 world.
- [ ] Match server editor staging/save behavior exactly.
- [x] Rename package and all resource/mixin references from `fr.isaac` to `fr.zazac1`.

## Required client screens

- [ ] `ConfigScreen`: exact home layout, target state, explicit Save and back-stack behavior.
- [ ] `RecipeTargetSelectScreen`: exact world list, metadata, thumbnails, search, scrolling, and Global Library entry.
- [ ] `CustomRecipesScreen`: exact library table, detail panel, empty state, Quick Add sidebar and sizing.
- [ ] `RecipeBuilderScreen`: exact picker, preview, result count input, and save/cancel behavior.
- [ ] `BuiltinRecipesScreen`: exact Quick Add source/detail behavior.
- [ ] `VanillaRecipesScreen` and `VanillaRecipeDetailsScreen`: exact layout, search, Save behavior, and target restrictions.
- [ ] `SaveChangesScreen`, target badge, sprite renderer, and navigation history.

## Entry points and messages

- [ ] Pause-menu icon opens a target-locked current-world editor.
- [ ] First-day chat tip is emitted once per physical local world; its click opens pause then the local editor.
- [ ] Remote multiplayer helper never opens a local editor.
- [ ] Server permissions and every configuration/query payload match the 1.21.11 protections.

## Assets and localization

- [x] 1.3.0 icon, language files, and GUI texture set copied from 1.21.11.
- [ ] Wire every translated key and sprite into the adapted 26.2 screens.

## Manual regression follow-up

- [ ] Every item in `PORT_TEST_CHECKLIST_1.3.0.md` passes in game.
- [ ] Visual screenshots are compared against the 1.21.11 UI before release.
