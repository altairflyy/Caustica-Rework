# AER-013 result

## Runtime integration

`RtComposite` now instantiates one `TemporalState` coordinator. The existing
AER-012 `recordTemporalReset(...)` seam delegates directly to
`TemporalState.collect(...)`; there is no parallel pending bitset in
`RtComposite`.

The coordinator receives the immutable AER-011 `FrameContext` in
`recordFrame(...)`. `TemporalState.snapshot(...)` keeps the first snapshot for
each `frameIndex`, and the same snapshot is reused if delivery occurs more
than once during that frame.

Legacy reset recipients are delivered through `TemporalState.broadcast(...)`
at their existing event sites:

- F3+A failure-latch recovery;
- resource reload entity-texture invalidation;
- resolution recreation of SVGF, NRD, motion-vector and wave state;
- FSR and XeSS camera-discontinuity reset requests.

The coordinator clears reasons only after the delivery callback returns. A
consumer exception therefore leaves the request pending for the next retry.
The coordinator owns no images, buffers, pipelines, or backend resources.

When a pre-frame invalidation has no `FrameContext` yet, the legacy delivery
is performed directly at the original call-site and acknowledged only after
successful completion, preserving startup timing.

## Characterization

`RtRewriteCharacterizationTest` now verifies the coordinator-backed legacy
reset recipients and the single runtime `TemporalState` authority. The
existing reset invariants remain unchanged; no shader, ReSTIR, SHaRC, SVGF
ownership, upscaler algorithm, LOD, or configuration changes were made.

## Validation

- `TemporalStateTest`: PASS.
- `TemporalResetReasonTest`: PASS.
- `RtRewriteCharacterizationTest`: 7/7 PASS.
- V1 `validate-fast.ps1`: `BASELINE-EQUIVALENT`, exact 4/4 failures.
- V2 `validate-build.ps1`: PASS.
- `git diff --check`: PASS.
- Forbidden-change audit: PASS; no shader changes and no new `waitIdle`.

State: DONE.
