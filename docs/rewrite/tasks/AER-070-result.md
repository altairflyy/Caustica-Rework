# AER-070 result

## Pre-audit and runtime seam

`RtComposite` previously materialized dimension, partial/game time, weather,
sun/moon, fog and cloud lanes as independent locals, with some helpers reading the
frame clock again. The values already existed and were written together into the
unchanged `WorldPushData` ABI.

`EnvironmentParameters` now provides one immutable per-frame snapshot. Production
`RtComposite` resolves the legacy values once at the same path-trace seam and reads
all environment lanes through that snapshot. Existing formulas, constants,
configuration reads and shader fields remain unchanged. Fog and cloud calculation
ownership is intentionally left for AER-071 and AER-072 respectively.

## Validation

- `EnvironmentParametersTest`, fog characterization and rewrite characterization: PASS.
- The fog characterization now follows the environment snapshot while preserving
  its original source, co-materialization and ABI-order invariants.
- `validate-build.ps1`: PASS, covering V0, V1 and V2.
- V1: 196 tests, exact 4/4 canonical baseline failures; characterization 7/7 PASS.
- V2: Gradle build PASS.
- V3: not required for this parameter-boundary extraction.
- Forbidden-change audit: no shader ABI/math, environment formulas/constants,
  feature flags, resource ownership, synchronization, or hot-path `waitIdle` changes.
