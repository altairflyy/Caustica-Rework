# AER-060 result

## Pre-audit and contract seam

The current frame path already exposes three conceptual TLAS instance sources:
full terrain, dynamic entities and published LOD. Light resources and the material
table are also published frame inputs, but no canonical scene-generation token
exists yet.

`RtScene` therefore defines only those six required values. It reuses the existing
`RtAccel.Instance` descriptor, keeps the three sources distinct, and snapshots the
lists without introducing databases or GPU-resource ownership.

`sceneGeneration` is explicit and independent. Its legacy/unversioned value is
zero; light generation and material epoch remain separate fields and are not used
as aliases.

## Validation

- `RtSceneTest`: PASS (3/3).
- `validate-build.ps1`: PASS; V0 PASS, V1 BASELINE-EQUIVALENT with
  exact canonical 4/4 failures and characterization 7/7, 189 tests executed,
  V2 PASS.
- Contract audit: no runtime adoption claimed or required before AER-061..064.
- Forbidden-change audit: no shader, math, resource ownership, TLAS assembly,
  configuration, backend, LOD/provider, or `waitIdle` changes.
- No generic scene databases or parallel TLAS path introduced.
