# GATE-1 result

## Strengthened integration audit

| AER | Status | Evidence | Gap | Required recovery |
|---|---|---|---|---|
| AER-010 | COMPLIANT | `RtDeviceBringup.capabilities()` produces the read-only `GpuCapabilities` snapshot; bring-up and feature enabling remain in `RtDeviceBringup`. | None. | None. |
| AER-011 | COMPLIANT | `RtComposite.recordFrame(...)` creates and passes `FrameContext` to `TemporalState`; `getRealtimeDeltaTicks() * 0.05f` is an explicit seconds conversion; `FrameContext.LEGACY_SCENE_GENERATION` is `0`, not light/material generation. | No canonical scene-generation token exists in the legacy renderer. | None; retain explicit unversioned `0`. |
| AER-012 | COMPLIANT | Production call-sites emit `MANUAL`, `RESOURCE_RELOAD`, `MATERIAL_GENERATION_CHANGE`, `RESOLUTION_CHANGE`, and `TELEPORT` in `RtComposite`; legacy recipients and timing are preserved. | No distinct legacy call-sites exist for `WORLD_CHANGE`, `DIMENSION_CHANGE`, `CAMERA_CUT`, or `FOV_CHANGE`; they were not invented. | None; documented in AER-012 result. |
| AER-013 | COMPLIANT | `RtComposite` owns one runtime `TemporalState`; all mapped reasons call `collect`, frame snapshots are deduplicated by `frameIndex`, and reset delivery uses `broadcast` with failure retention. | None. | None. |
| AER-014 | COMPLIANT | `RestirHistory` owns reservoirs, write parity, enabled state, allocation/clear and destruction; `RtComposite` uses delegating address/mode/advance helpers only. | None. | None. |
| AER-015 | COMPLIANT | `SharcRadianceCache.INSTANCE` is used by `RtComposite`, the terrain full-clear path and the options reset path; it owns scene tracking, reset, debug state, parameters, address and grid origin while `RtSharc` remains implementation detail. | None. | None. |
| AER-016 | COMPLIANT | `SvgfResources` is instantiated by `RtComposite` and owns history/moments/filter images, previous view-Z/normal, parity, `hasHistory`, and previous camera; `RtComposite` retains only dispatch orchestration. | None. | None. |

No dead facade or duplicate migrated ownership remains in the production
path. The old `gate/1-temporal-state` tag is historical and was not modified;
this report is the strengthened canonical closure.

## Validation

- V0 `git diff --check`: PASS.
- V1 `validate-fast.ps1`: PASS, `BASELINE-EQUIVALENT`; exact 4/4 frozen
  shader-regression failures, characterization 7/7 PASS.
- V2 `validate-build.ps1`: PASS.
- V3 Vulkan smoke: NOT AVAILABLE; no configured smoke host or script exists
  in this checkout.
- Shader/math audit: PASS; no shader-source, descriptor-layout, ReSTIR,
  SHaRC, SVGF, or upscaler math changes in the AER-010..016 recovery range.
- `MIGRATION_STATE.yaml`: AER-010..016 `DONE`, `active_task: null`,
  `GATE-1: PASS`, AER-020 remains `PENDING`.

Conclusion: **GATE-1 integration completeness: PASS**.
