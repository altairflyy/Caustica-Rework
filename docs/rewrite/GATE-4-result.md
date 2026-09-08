# GATE-4 result

## Scope

Audit of the strengthened GATE-4 acceptance against the production runtime
after commits `9bc9aa6` through `b21bf1b` (AER-040..046). No production code
was modified during this gate audit.

## Task compliance

| AER | Status | Production evidence |
| --- | --- | --- |
| AER-040 | PASS | One `DeferredDeletionQueue` is owned by `RtContext`; published retirements delegate the unchanged `GraphicsUse` or `TrackedGraphicsUse` token to the sole `RtGpuExecutor.destroyJobs` authority. |
| AER-041 | PASS | `GPU_OWNERSHIP.md` names the owners, creation, last-use, destruction, queue domain and recreation trigger observed in current production code. |
| AER-042 | PASS | One `AccelerationStructureManager` is owned by `RtContext`; its API delegates the existing BLAS/refit/compaction/TLAS implementation to `RtAccel` without translating geometry or flags. |
| AER-043 | PASS | `RtLodTerrain` routes migrated build, compaction, scratch, destroy and retirement operations through the manager at the original publication/cancellation seams. |
| AER-044 | PASS | `RtTerrain` and `RtSectionBuilder` route migrated terrain AS operations through the manager; unpublished cleanup remains in its distinct executor domain. |
| AER-045 | PASS | Transient, static, updatable and refit entity BLAS paths use the manager while preserving ring selection, topology checks, build ordering and tracked-use retirement. |
| AER-046 | PASS | Live AS count/BLAS bytes observe the common `RtAccel` lifetime seam; pending-retirement/queue-depth counters observe the real executor queue and are sampled once per frame. |

## Integration completeness

| Requirement | Status | Evidence |
| --- | --- | --- |
| Real ownership inventory | PASS | `GPU_OWNERSHIP.md` was refreshed after AER-046 and distinguishes published, unpublished, ring-slot and idle-teardown domains. |
| Deferred deletion adoption | PASS | Static audit finds no production call from migrated owners directly to `RtGpuExecutor.retireAfterGraphics(...)`. |
| AS manager runtime boundary | PASS | LOD, terrain, entity and frame TLAS call through `RtContext.accelerationStructures()`. |
| Critical destruction bypass | PASS | Characterization finds none outside `RtAccel` and its manager. `TlasRing` internal completed-slot cleanup/idle teardown is the documented aggregate-owner exception. |
| Duplicate ownership | PASS | No parallel manager queue or AS resource copy exists; the facades own neither Vulkan allocations nor timeline state. |
| Lifetime invariants | STATIC PASS | Existing last-use tokens, publication ordering, unpublished cleanup and teardown ordering are preserved; no new hot-path `waitIdle` exists. Runtime proof remains blocked below. |
| Geometry/flags/refit/compaction | PASS | Gate diff contains no shader files or rendering-math changes; Vulkan build implementation remains in `RtAccel`. |
| AER-046 diagnostics | PASS | Counters read the actual AS constructors/destructor and executor destroy queue, then publish through the existing frame-statistics path. |

## Validation

- Targeted GPU/LOD/terrain/entity/characterization suite: PASS.
- V1 `validate-fast.ps1`: PASS, `BASELINE-EQUIVALENT`; 176 tests with exact
  4/4 frozen shader failures and characterization 7/7 PASS.
- V2 `validate-build.ps1`: PASS with configured DLSS and Vulkan SDKs and the
  same exact baseline comparison.
- Shader diff across GATE-4: NONE.
- Added hot-path `waitIdle`: NONE.
- Candidate JAR: current build copied byte-for-byte to the Modrinth `prova`
  instance as the only active `caustica-0.2.4.jar`; the previous `0.2.2` JAR
  is preserved in the instance backup directory.
- Vulkan validation with the current candidate: NOT TESTED.
- LOD/terrain/entity runtime smoke with the current candidate: NOT TESTED.

The installed Modrinth App exposes no working direct-profile launch argument,
and the available automation surface cannot control native Modrinth/Minecraft
windows. The previous `latest.log` predates all GATE-4 commits and therefore is
not accepted as runtime evidence.

## Conclusion

`GATE-4 integration completeness: PASS (static/runtime wiring)`.

`GATE-4 overall: BLOCKED` pending the mandatory current-build Vulkan validation
and LOD/terrain/entity smoke. `GATE-4` remains `PENDING`; `AER-050` remains
`PENDING` and was not started.
