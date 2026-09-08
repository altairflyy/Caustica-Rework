# AER-045 result

## Pre-audit

The entity runtime has transient particle BLAS, persistent block-entity BLAS,
and persistent entity ring slots that either rebuild or refit in place after
their exact prior graphics use completes. Frame-list reuse releases transient
BLAS and build/refit scratch only after its tracked use is complete.

Route these existing prepare, refit, release, destroy and retirement seams
through `AccelerationStructureManager`. Preserve ring selection, topology
checks, rebuild interval, instance data, animation capture and synchronization.

## Validation

- Targeted manager, deferred queue, entity/weather and rewrite
  characterization tests: PASS.
- V1: PASS, 174 tests; exact 4/4 canonical failures, characterization 7/7.
- V2: PASS with configured DLSS/Vulkan SDKs and the same baseline comparison.
- `git diff --check`: PASS.
- V3 entity/Vulkan smoke: NOT TESTED; required at GATE-4.

## Integration and equivalence evidence

- Transient particle BLAS preparation and frame-list release delegate through
  the manager while retaining the same pooled list and reuse boundary.
- Block-entity and ordinary entity static builds forward the original addresses,
  vertex counts, bucket topology and labels to the existing RtAccel methods.
- Updatable builds and refits retain the exact `canUpdate` predicate, index
  topology check, ring-slot selection, scratch sizing and rebuild interval.
- Entity BLAS recording remains before the existing memory barrier and TLAS
  build in `RtComposite`; only its receiver changed to the manager.
- Persistent AS/backing replacement after slot availability, deferred eviction
  with the original tracked graphics-use token, and shutdown after device idle
  all delegate through the manager.
- Refit/build scratch release is manager-routed at the same completion/reuse
  points. Entity mesh, motion, table and shading buffers retain their owners.
- The entity table buffer continues to use `DeferredDeletionQueue` directly;
  it is not an acceleration-structure ownership path.

## Forbidden-change audit

No shader, animation capture, motion-vector data, topology logic, instance
transform, Vulkan flag, geometry, algorithm, tuning or new wait-idle change.

## Outcome

`AER-045` is DONE. `AER-046` is READY and was not started.
