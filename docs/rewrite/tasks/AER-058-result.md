# AER-058 result

## Pre-audit and runtime seam

`RestirHistory` already owned reservoir allocation, addresses and parity, but
`RtComposite` still selected its lifecycle and assembled tuning/bindings.
`SharcRadianceCache` owned persistent state, while its per-frame lifecycle policy
and five shader inputs were still expanded at the composite call site.

## Integration

- `RestirSystem` is the production facade over `RestirHistory`; it owns live
  enable/allocation policy and publishes one immutable binding snapshot containing
  previous/current addresses, mode and unchanged tuning values.
- `SharcRadianceCache.sync(...)` owns the existing scene/reset/debug lifecycle
  policy, and `bindings(...)` publishes one immutable cache/parameter snapshot.
- `RtComposite` passes those snapshots to the unchanged generated world data and
  push-constant layouts.
- ReSTIR previous/current order and parity advance timing are unchanged.
- No shader, ReSTIR math or SHaRC math changed.

## Validation

- `RestirSystemTest`, `RestirReservoirMathTest`, `SharcRadianceCacheTest`,
  `RtRewriteCharacterizationTest` and `RtShaderConstantMirrorTest`: PASS.
- V1: BASELINE-EQUIVALENT, exact canonical 4/4 failures; characterization
  7/7 PASS; 186 tests executed.
- V2: PASS.
- `git diff --check`: PASS.
- Forbidden-change audit: no shader/math changes and no new `waitIdle`; the
  existing rare SHaRC clear synchronization moved unchanged into its facade.
