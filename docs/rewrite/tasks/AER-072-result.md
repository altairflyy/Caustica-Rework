# AER-072 result

## Pre-audit and runtime seam

The legacy cloud authority was split between `RtComposite` (feature config and
the three `WorldPush` lanes) and `RtCloudCells` (resource-pack occupancy cache).
The shader already integrated clouds in camera misses, path segments and direct
light visibility; those shader paths are unchanged.

`CloudModule` is now the single production owner for cloud configuration,
parameter materialization and the authored `clouds.png` cache. It returns the
existing `EnvironmentParameters.Clouds` snapshot, while `RtComposite` only
uploads the cached words and preserves the existing push ABI/order. Resource
reload invalidation now targets the module directly; `RtCloudCells` was removed,
so no parallel cache or parameter authority remains.

## Preserved behavior

- Camera visibility remains in `world.rmiss` through `cloudLayer`.
- Camera, reflection and refraction path segments retain `cloudSegment` in the
  path integral with the existing `showCelestial` quality selection.
- Cloud shadows retain the existing scene-visibility-first ordering and use the
  same shader query.
- Coverage, opacity, shadow strength, height, thickness, world-time drift,
  anchor wrapping, view limit, vanilla cloud color and fallback cell-map
  behavior use the legacy constants and formulas.
- Nether and End still publish `EnvironmentParameters.Clouds.NONE`.

## Validation

- Targeted `CloudModuleTest`, `EnvironmentParametersTest`, full cloud regression
  suite and rewrite characterization: PASS.
- `validate-build.ps1`: PASS, covering V0, V1 and V2.
- V1: 202 tests, exact 4/4 canonical baseline failures; characterization 7/7 PASS.
- V2: Gradle build PASS.
- V3: not required for this ownership-only Java extraction.
- Forbidden-change audit: no shader source/ABI/math, cloud formulas/constants,
  visibility/reflection/shadow paths, GPU ownership or hot-path `waitIdle` changes.
