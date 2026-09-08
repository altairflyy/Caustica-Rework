# AER-073 result

## Pre-audit and runtime seam

`RtWeatherCapture` already reconstructed rain and snow from vanilla's live
`WeatherRenderState` into the shared particle capture. That geometry was then
uploaded as one particle BLAS and published as one dynamic entity TLAS instance,
but the weather producer exposed only a column count, leaving its AS-facing
vertex segment implicit in `RtEntities`.

`RtWeatherCapture.sceneContribution(...)` now publishes an immutable
`WeatherSceneContribution` containing the column count and exact appended vertex
range. `RtEntities` consumes that contribution for zero-motion materialization,
logical accounting and the existing shared particle BLAS path. The contribution
owns no GPU resources and does not create a parallel TLAS path.

## Preserved behavior

- Rain and snow still use vanilla's extracted column lists, textures,
  heightmap-clipped extents, UV scrolling, orientation, alpha and distance fade.
- Particle-budget ordering and limits are unchanged.
- Weather vertices still receive zero motion and remain tagged as unlit weather.
- Weather remains in the particle BLAS with `PARTICLE_BIT`, primary-only mask and
  identity transform; no additional BLAS or TLAS instance is introduced.
- Existing fail-soft behavior and partial-capture accounting are unchanged.

## Validation

- `RtWeatherCaptureTest`, `WeatherSceneContributionTest`, `SceneAssemblerTest`
  and rewrite characterization: PASS.
- `validate-build.ps1`: PASS, covering V0, V1 and V2.
- V1: 206 tests, exact 4/4 canonical baseline failures; characterization 7/7 PASS.
- V2: Gradle build PASS.
- V3: not required for this ownership-only scene-adapter extraction.
- Forbidden-change audit: no shader source/ABI/math, rain/snow reconstruction,
  instance mask/custom index/SBT semantics, GPU lifetime or hot-path `waitIdle`
  changes.
