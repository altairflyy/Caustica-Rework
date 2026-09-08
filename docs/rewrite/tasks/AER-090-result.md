# AER-090 result

## Pre-audit and runtime seam

`RtComposite` already consumes ReSTIR history through `RestirSystem` and SVGF
history through `SvgfReconstructionBackend`; it owns no parallel history images
or parity state. LOD storage and shutdown already belong to `RtLodTerrain`, with
`RtComposite` limited to the per-frame update and immutable scene contribution.

The remaining directly owned migrated lifetimes are the frame TLAS ring and the
FSR/XeSS/native upscaler backend instances. AER-090 moves the TLAS ring into the
existing device-owned `AccelerationStructureManager` and the upscaler instances
into a domain runtime, while preserving every frame call site and its ordering.

## Implementation and acceptance evidence

- `RtComposite` contains no ReSTIR/SVGF history images or temporal parity
  fields; those remain owned by `RestirSystem`/`RestirHistory` and
  `SvgfReconstructionBackend`/`SvgfResources`.
- The frame TLAS ring is device-owned by `AccelerationStructureManager`, built
  through the same facade and destroyed by `RtContext` after GPU executor
  shutdown. Build, descriptor publication, record and barrier ordering is
  unchanged.
- `UpscalerRuntime` owns the FSR, XeSS and native backend instances, their
  switch-away release operation and final destruction. `RtComposite` performs
  only selection, request construction and dispatch delegation.
- LOD storage and shutdown remain with `RtLodTerrain`; `RtComposite` only
  requests the per-frame update and consumes its immutable scene contribution.
- The existing DLSS-RR, NRD, ReSTIR and SVGF domain owners and lifecycle timing
  are unchanged.

## Validation

- Targeted ownership, AS manager, rewrite characterization and upscaler tests:
  PASS, 18/18.
- `validate-build.ps1`: PASS; V0 + V1 + V2 demonstrated in one run, 247 tests,
  exact 4/4 canonical failures and characterization 7/7 PASS.
- `git diff --check`: PASS.
- Forbidden-change audit: PASS; no shader, shader math, synchronization,
  feature-selection, LOD implementation, tuning or new `waitIdle` changes.
- V3: NOT RUN; AER-090 changes ownership boundaries only and does not request a
  task-specific runtime smoke.

State: DONE. AER-091 is READY and was not started.
