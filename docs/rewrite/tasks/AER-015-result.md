# AER-015 result

## Runtime ownership

`SharcRadianceCache.INSTANCE` is now the production boundary used by
`RtComposite`. `RtSharc` remains the implementation detail for GPU buffer
allocation, clear and destruction.

The migrated responsibilities all cross the new boundary:

- world/dimension tracking via `sceneChanged(...)`;
- clear requests and cache reset via `requestClear`, `clearRequested` and
  `clearNow`;
- enable/disable lifecycle and destruction via `ensure`,
  `releaseIfDisabled` and `destroy`;
- debug active state, frame cadence and summary materialization;
- live SHaRC parameter materialization (`params`, `params2`, `params3`);
- cache address and grid-origin access used by the unchanged `WorldPush`
  assembly.
- manual UI reset and terrain full-clear reset requests also delegate through
  the same facade.

The duplicate scene/debug fields and direct `RtSharc.INSTANCE` accesses were
removed from `RtComposite`. No SHaRC cache policy, shader parameter layout,
reset event, timing, or shader/math behavior changed.

## Validation

- `SharcRadianceCacheTest`: PASS.
- `RtRewriteCharacterizationTest`: PASS.
- V1 `validate-fast.ps1`: `BASELINE-EQUIVALENT`, exact 4/4 failures;
  characterization 7/7 PASS.
- V2 `validate-build.ps1`: PASS.
- `git diff --check`: PASS.
- Forbidden-change audit: PASS; no shader changes, no SHaRC math changes and
  no new `waitIdle`.

State: DONE.
