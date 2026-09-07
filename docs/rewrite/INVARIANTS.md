# Caustica Rewrite Invariants

These invariants are mandatory throughout the migration. A task that cannot preserve
or demonstrate the invariants it touches must not be marked DONE.

## GPU

### GPU-001
No GPU resource is destroyed before completion of its last use.

### GPU-002
A BLAS referenced by a published TLAS cannot be destroyed.

### GPU-003
A resource shared between queues must retain synchronization/ownership equivalent to
or stronger than the frozen reference.

### GPU-004
No new `waitIdle` on the hot path.

## Temporal

### TMP-001
Frame N reads history only from previous frames.

### TMP-002
Camera cut, teleport, and dimension change invalidate incompatible temporal state.

### TMP-003
Resolution change invalidates history buffers whose dimensions no longer match.

### TMP-004
A temporal backend cannot receive `current` and `previous` aliased simultaneously
unless the reference explicitly expects that aliasing.

## ReSTIR

### RST-001
The previous reservoir is read-only while generating the current reservoir.

### RST-002
Ping-pong index semantics remain identical to the reference.

### RST-003
Reservoir mathematics does not change during `OWNERSHIP_ONLY` tasks.

### RST-004
Incompatible scene/light generation invalidates reuse.

## LOD

### LOD-001
The published proxy remains valid while its replacement is being prepared.

### LOD-002
Coarse geometry is removed only when the required fine coverage is complete.

### LOD-003
Unchanged `sourceKey + sourceVersion` permits reuse.

### LOD-004
Batch size remains bounded.

### LOD-005
World/dimension transitions do not reuse snapshots from the previous dimension.

### LOD-006
Provider failure does not retire valid full terrain.

### LOD-007
DH and Voxy are not simultaneous owners of the same distant horizon.

## Reconstruction / upscale

### REC-001
Only one primary temporal reconstruction/denoising path runs per frame.

### UPS-001
Only one temporal upscaler runs per frame.

### UPS-002
Jitter, render resolution, and history reset remain coherent with the active
upscaler.

## Compatibility

### CMP-001
Caustica core does not depend on Iris.

### CMP-002
The core does not require DH or Voxy to start.
