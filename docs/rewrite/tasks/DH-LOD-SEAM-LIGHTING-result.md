# DH LOD seam lighting — STATIC PASS, RUNTIME PENDING

## Problem

The captured frame shows a hard energy/colour discontinuity at the RT-to-Distant-Horizons boundary.
Native DH colour was already display-referred RGBA8, while Caustica RT was still scene-linear and
passed through automatic exposure plus the selected SDR/HDR tone mapper. The previous far-lighting
option only multiplied the already-lit DH colour by a mean-preserving normal correction, so it could
not make the two sides share a lighting or display transform. Water also remained a flat native colour
with a late reflection blend.

## Implemented contract

- The existing same-pass DH auxiliary attachment now stores unlit RGB albedo plus one packed UNORM8
  byte: exact axis-aligned face, water bit, two-bit sky light and two-bit block light. No DH geometry is
  captured, retained or redrawn.
- Terrain LOD lighting is reconstructed in scene-linear space from the same celestial direction and
  radiance scale used by RT, the current RT miss as the same-frame sky/weather proxy, and DH's own
  sky/block-light state.
- Native-only pixels remain fully opaque. The RT/DH overlap is feathered only where both renderers
  contain geometry, avoiding a blend toward the RT sky miss at the outside edge.
- Distant water uses its unlit biome albedo as the absorption/body term and combines the same-frame sky
  with the existing far-field reflection through a Fresnel response. Packed material data is sampled
  with nearest filtering so interpolation cannot manufacture water/light bits.
- Relit terrain and water now pass through the same exposure and SDR/HDR tone mapper as RT. Disabling
  `terrain.dh-far-lighting` still preserves the previous native-DH display path verbatim.
- The temporary full-screen reflection inspection path was removed. Display push constants were reduced
  from 132 to the Vulkan baseline guarantee of 128 bytes.
- The DH reflection proxy now retires replaced/evicted BLAS resources after their graphics-use token and
  routes destruction through `AccelerationStructureManager`; no hot-path device-idle wait was added.

The auxiliary attachment grows from R8 to RGBA8. This costs three additional bytes per DH pixel
(approximately 10.5 MiB at the captured 2559x1439 resolution; 23.7 MiB at 3840x2160) and avoids a
second geometry pass or a large multi-buffer G-buffer.

## Validation

- Targeted DH surface, hybrid display, packed UNORM8 round-trip and AS-manager tests: PASS.
- `scripts/agent/validate-build.ps1`: PASS.
- JUnit: 337/337 PASS; expected failures 0; unexpected failures 0.
- Rewrite characterization: 7/7 PASS.
- GLSL and Slang compilation plus SPIR-V validation: PASS.
- Gradle package build: PASS.
- `git diff --check`: PASS (line-ending conversion warnings only).
- Candidate artifact: `build/libs/caustica-0.1.1.jar`, 24,037,522 bytes,
  SHA-256 `CFD3AA6F5EA9167CB65EF92598CA44993A1716D6F556489920D1E62893B49248`.

## Remaining qualification

Static/build evidence cannot establish that the seam is visually acceptable or that the new display
pass stays within the desired frame budget on the target GPU. A same-view runtime capture must check
daylight terrain, the water boundary, camera motion across the transition and a night/block-light case.
The runtime log must prove `dhFarLighting` and the DH reflection path are active, and GPU timings must be
compared with the previous candidate. Until that evidence exists, visual result and performance are
`NON DIMOSTRATO`, not PASS.

Decision: **NEXT EXPERIMENT**.
