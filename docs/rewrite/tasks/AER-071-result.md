# AER-071 result

## Pre-audit and runtime seam

Fog is already integrated as a participating medium by `fog.slang` in the path
integral. The only Java-side fog authority was the pair of config/probe resolvers
in `RtComposite`, materialized into adjacent `WorldPushData` lanes through
`EnvironmentParameters`.

`FogModule` now exclusively materializes those config and vanilla `FOG_COLOR`
bindings. `RtComposite` delegates once per frame and retains no duplicate fog
calculation. The environment snapshot and push-field order remain unchanged; fog
is not moved to post-processing and owns no GPU resources or history.

## Validation

- `FogModuleTest`, `EnvironmentParametersTest`, full fog regression suite and
  rewrite characterization: PASS.
- Fog characterization follows `FogModule` and still verifies config ordering,
  vanilla `FOG_COLOR`, joint params/tint materialization and push ABI order.
- `validate-build.ps1`: PASS, covering V0, V1 and V2.
- V1: 197 tests, exact 4/4 canonical baseline failures; characterization 7/7 PASS.
- V2: Gradle build PASS.
- V3: not required for this ownership-only Java extraction.
- Forbidden-change audit: no shader source/ABI/math, fog constants/formulas,
  participating-medium placement, resource ownership, or hot-path `waitIdle` changes.
