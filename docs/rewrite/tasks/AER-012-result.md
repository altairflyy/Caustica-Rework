# AER-012 result

## Production mapping

The legacy reset events now produce explicit `TemporalResetReason` values in
`RtComposite`, while continuing to reach the same legacy recipients with the
same order, timing, and effective reset count:

- `MANUAL` — F3+A render-state invalidation via `resetFailureLatch()`.
- `RESOURCE_RELOAD` — `onResourceReloadStart()`.
- `MATERIAL_GENERATION_CHANGE` — the material-generation invalidation in
  `onResourceReloadStart()`.
- `RESOLUTION_CHANGE` — resource/render-extent recreation in `ensureOutput()`.
- `TELEPORT` — the camera discontinuity threshold above 32 blocks, before the
  existing FSR or XeSS `requestReset()` call.

No distinct legacy reset call-site was identified in AER-012 for:

- `WORLD_CHANGE`
- `DIMENSION_CHANGE`
- `CAMERA_CUT`
- `FOV_CHANGE`

These reasons were not generated artificially. In particular, the current
renderer explicitly treats FOV changes as ordinary reprojection rather than a
history reset.

The following are internal backend state transitions, not separate external
reset events:

- `RtNrdDenoiser.INSTANCE.resetHistory()` after resolution-dependent resource
  recreation.
- `resetHistory = true` in `RtDlssRr` after creation of a fresh feature.

The reset bitset is now owned and consumed by the AER-013 coordinator; this
task did not redirect legacy reset timing.

## Validation

- `TemporalResetReasonTest`: PASS.
- V1 `validate-fast.ps1`: `BASELINE-EQUIVALENT`, exact 4/4 baseline failures.
- `RtRewriteCharacterizationTest`: 7/7 PASS.
- V2 `validate-build.ps1`: PASS.
- Shader/math changes: none.

State: DONE.
