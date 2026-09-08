# AER-091 result

## Pre-audit

The canonical removal order is LOD, temporal state, reconstruction, upscaler,
scene, pipeline and render graph.

- LOD already has one production authority (`RtLodTerrain` plus the selected
  neutral source); `RewriteGates.lodV2()` has no production call site.
- `TemporalState` is already the sole pending-reason/snapshot authority;
  `RewriteGates.temporalV2()` has no production call site.
- Reconstruction backend selection is already singular. Its remaining A/B is
  the qualified denoiser barrier emitter.
- Upscaler backend selection is already singular. Its remaining A/B is the
  qualified upscaler barrier emitter.
- `SceneAssembler` is already the sole authoritative TLAS input assembly path;
  `RewriteGates.sceneV2()` has no production call site.
- `GraphExecution` still selects between the legacy `FramePipeline.Cursor` and
  graph cursor.
- Qualified POST and path-trace barrier emitters still retain their legacy A/B
  branches.

AER-091 removes only these reachable legacy branches. Development-key removal
remains AER-092.

## Removal sequence

1. **LOD:** already single-authority; no reachable legacy branch removed.
2. **Temporal state:** already single-authority; no reachable legacy branch
   removed.
3. **Reconstruction:** removed the manual fallback from `DenoiserBarriers`;
   SVGF and the quarantined NRD seam now emit only the qualified generated
   barriers.
4. **Upscaler:** removed the manual fallback from `UpscalerBarriers`; native,
   DLSS-RR, FSR and XeSS export seams now use only the qualified generated
   path when available/selected.
5. **Scene:** already single-authority through `SceneAssembler`; no reachable
   legacy branch removed.
6. **Pipeline:** removed `FramePipeline.Cursor` selection from
   `GraphExecution`; callbacks always execute in validated graph order.
7. **Render graph:** removed the manual fallback from POST and path-trace
   emitters, including the obsolete selection parameters in `RtExposure`.

The barrier plans, command boundaries, broad stage/access masks, callback order
and exception/advancement semantics are unchanged. `RewriteGates` remains as a
now-unreferenced development-key container for the separate AER-092 cleanup.

## Validation

- Reconstruction checkpoint: targeted denoiser/SVGF/characterization tests
  PASS; `validate-build.ps1` PASS with exact 4/4 baseline and characterization
  7/7.
- Upscaler checkpoint: targeted native/FSR/XeSS/barrier/characterization tests
  PASS; `validate-build.ps1` PASS with exact 4/4 baseline and characterization
  7/7.
- Pipeline checkpoint: targeted graph/pipeline tests PASS;
  `validate-build.ps1` PASS with 248 tests, exact 4/4 baseline and
  characterization 7/7.
- Final render-graph checkpoint: 36 targeted tests PASS;
  `validate-build.ps1` PASS with 250 tests, exact 4/4 baseline,
  characterization 7/7 and V2 PASS.
- V3 after removal: PASS, user-completed DLSS-RR Vulkan smoke with
  synchronization validation, first frame/reset, more than 30 seconds
  stationary, movement/rotation, menu, world reload and shutdown. Evidence:
  `build/rewrite-validation/GATE-8-validation/B-883bda6e50ec4e5c8ed1515f9e534c92`.
- V3 findings are baseline-equivalent: four known DH vertex-input VUID
  appearances, two known NGX DLSSD WAW hazards and two appearances of the same
  known shutdown leak report. No new VUID, synchronization hazard, device loss
  or crash occurred.
- Candidate: `caustica-0.2.8-aer091-candidate.jar`, SHA-256
  `11CC8FFE598B81350A436179793B6330DD10323C6E686422D30658978CA91C1D`.
- Forbidden-change audit: no shader/math, resource ownership, queue scheduling,
  stage/access mask, feature policy, tuning or new `waitIdle` changes.

State: DONE. AER-092 is READY and was not started.
