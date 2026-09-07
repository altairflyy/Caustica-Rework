# AER-011 result

Implemented the immutable `FrameContext` contract and focused tests.

The snapshot contains only frame data already represented by the legacy
renderer: frame serial, render delta, extents, current/previous camera,
jitter, world identity, dimension, and scene generation.

`DeltaTracker.getRealtimeDeltaTicks()` is the intended source for
`deltaTimeSeconds`; `getGameTimeDeltaPartialTick()` remains interpolation data
and is not a frame duration. The existing `frameCounter`, `displayW/H`,
`renderW/H`, camera matrices, and `CausticaJitter` are the corresponding
legacy sources.

There is no canonical scene-generation token in the current code. The record
therefore uses `LEGACY_SCENE_GENERATION == 0` as an explicit unversioned value;
it does not alias light or material generation. `RtComposite`, shader push
layouts, resource ownership, and jitter behavior remain unchanged.

Runtime recovery: `RtComposite.recordFrame` now creates one read-only snapshot
per legacy composite frame. Realtime ticks are explicitly converted to seconds
with the runtime tick duration (`* 0.05f`); partial tick remains interpolation
state and is not used as frame duration.

Validation:

- `FrameContextTest` PASS.
- V0 PASS.
- V1 PASS: exact frozen baseline, 9/9 accepted shader failures; characterization 7/7 PASS.
- V2 PASS: Gradle build successful (`:check` and `:build`).

Behaviour change: none. Shader math change: none.
