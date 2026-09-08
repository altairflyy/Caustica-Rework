# AER-064 result

## Pre-audit and runtime seam

The frame path already assembled terrain then LOD before entity capture and passed
that base segment plus dynamic entities to TLAS preparation. Light and material
views were read independently at the same frame seam. No canonical scene-generation
authority exists.

`SceneAssembler` now owns the exact terrain/LOD composition and publishes an
immutable `RtScene`. It verifies that entity capture used the same static segment,
then derives the ordered `TlasInput` directly from that scene. The runtime consumes
this TLAS input directly, so `RtScene` is not a dead parallel container.
Light/material generations remain separate, while scene generation stays explicitly
legacy/unversioned (`0`).

## Validation

- `SceneAssemblerTest`, all scene-contribution tests and rewrite characterization: PASS.
- TLAS input equivalence covers count, terrain/LOD/entity ordering, mask,
  custom index and SBT record offset.
- `validate-build.ps1`: PASS, covering V0, V1 and V2.
- V1: 194 tests, exact 4/4 canonical baseline failures; characterization 7/7 PASS.
- V2: Gradle build PASS.
- V3: not required for this ownership-only assembly seam.
- Forbidden-change audit: no shader math, scene-generation alias, entity/LOD capture,
  BLAS/TLAS fields, resource lifetime, synchronization, or hot-path `waitIdle` changes.
