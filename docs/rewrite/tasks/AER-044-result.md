# AER-044 result

## Pre-audit

`RtTerrain` builds one BLAS per packed section and optionally compacts it before
publication. Published geometry is retired with the last graphics-use token;
unpublished results are retired through the executor's separate unpublished
domain. Shutdown waits for active work and GPU idle before synchronous cleanup.

The migration routes terrain BLAS preparation, recording, scratch release,
compaction and AS destruction through `AccelerationStructureManager` at those
same seams. Meshing, packed arrays, geometry descriptors, flags, batching,
publication and synchronization remain unchanged.

## Validation

- Targeted manager, deferred queue, terrain, LOD and rewrite characterization
  tests: PASS.
- V1: PASS, 173 tests; exact 4/4 canonical failures, characterization 7/7.
- V2: PASS with configured DLSS/Vulkan SDKs and the same baseline comparison.
- `git diff --check`: PASS.
- V3 Vulkan smoke: NOT TESTED; runtime Vulkan validation remains required for
  GATE-4.

## Integration and lifetime evidence

- `RtSectionBuilder` prepares terrain/LOD BLAS through the manager with the
  original positions, vertex count, indices, bucket counts, opacity micromap,
  compaction flag and label.
- Each `PreparedSection` retains its creating manager, so worker cancellation
  and cleanup without a current global context still release scratch and AS
  through the same authority.
- `RtTerrain` delegates build recording, scratch release, compaction prepare,
  recording, completion and failure cleanup at the original executor seams.
- Published section replacement and asynchronous world clear retire AS through
  the manager with the original `GraphicsUse` token. Unpublished geometry and
  completed builds retain the executor's unpublished retirement domain.
- Synchronous shutdown still joins active tasks and waits for GPU idle before
  releasing terrain AS through the manager. No new wait-idle call was added.
- The obsolete direct `SectionGeom.destroy()` path was removed. Section table
  buffers and material/UV buffers retain their existing owners and timing.
- Static audit finds no direct terrain calls to the migrated `RtAccel` build,
  compaction, scratch-release or AS-destruction operations.

## Forbidden-change audit

No shader, terrain meshing, packed geometry, Vulkan flag, batching, publication,
synchronization, LOD policy, algorithm or tuning change is present.

## Outcome

`AER-044` is DONE. `AER-045` is READY and was not started.
