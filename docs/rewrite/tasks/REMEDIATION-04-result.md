# REMEDIATION-04 result — Extract WorldTraceResources

Status: COMPLETED

## Ownership transferred

`WorldTraceResources` is the production runtime owner of the world ray-tracing pipeline and its
inseparable lifecycle state:

- `worldPipeline` creation, binding, recreation and destruction;
- block-atlas sampler and bound-atlas identity;
- bindless texture capacity and material binding readiness;
- resource-reload rebind state and the material-epoch trace gate;
- world push-buffer ring, slot rotation and exact last-graphics-use tokens;
- borrowed output/guide descriptor views;
- world/material/sky-atlas binding lifecycle and celestial atlas metadata.

`RtComposite` retains one `WorldTraceResources` instance and high-level orchestration calls. It no
longer contains a parallel field, queue, ring or lifecycle authority for this domain. Existing
external owners (`RtMaterialRegistry`, `RtBlockMaterials` and `RtEntityTextures`) remain separate.

## Lifecycle preservation

- Initial creation retains the existing world/atlas readiness gate, pipeline shape, push-ring
  allocation, descriptor binding and material-epoch fallback frame.
- Bindless growth retains the existing idle-safe pipeline destruction and recreation seam.
- Resource reload retains reset delivery before the existing idle seam, then destroys the pipeline
  and material registry before rebinding to a fresh atlas handle.
- Frame-sized output and guide images remain borrowed; their views are rebound without transferring
  image ownership.
- Push-ring reuse retains six-slot rotation, an exact `TrackedGraphicsUse` per slot, exact-token wait
  before host reuse and marking with the current graphics-use token.
- Nominal shutdown destroys the owned pipeline, push buffers and sampler at the existing shutdown
  position.
- No new `waitIdle`, deferred-retirement path or shader/math change was introduced.

## TLAS boundary characterization migration

`AccelerationStructureManagerTest.productionFrameTlasUsesTheManagerWithoutChangingItsOrderingSeam()`
was coupled to the old literal `active.setTlas(...)` call in `RtComposite`. The test now requires the
canonical sequence:

`AccelerationStructureManager.buildTlas` -> `WorldTraceResources.bindTlas` with the exact prepared
handle -> `AccelerationStructureManager.recordTlas` -> Vulkan visibility barrier -> path-trace
invocation.

It also verifies that `bindTlas` delegates to `worldPipeline.setTlas`, that the old direct publication
path is absent, and that `WorldTraceResources` neither owns nor creates/caches TLAS authority. This is
a migration of stale structural characterization, not a relaxation of the invariant.

## Tests

Added `WorldTraceResourcesTest`:

- behavioral: immutable configuration validation, defensive copying of borrowed frame handles,
  six-slot rotation, and fresh-owner fallback/gate state;
- structural architecture: runtime owner wiring, absence of duplicate migrated fields in
  `RtComposite`, pipeline ownership in `WorldTraceResources`, and no reverse dependency.

Modified `AccelerationStructureManagerTest` only as described above. No source-text test is used as
behavioral proof of Vulkan execution.

## Validation

- Targeted `WorldTraceResourcesTest`: PASS.
- Targeted migrated TLAS test: PASS.
- `validate-fast.ps1`: PASS; exact 4/4 canonical failures; 0 unexpected failures;
  characterization 7/7 PASS.
- `validate-build.ps1`: PASS; 253 tests executed with exact 4/4 canonical failures and 0 unexpected
  failures; characterization 7/7 PASS; V2 build PASS.
- `git diff --check`: PASS.

## Test gaps

The current Vulkan/resource seams do not make device operations independently injectable. Therefore:

- actual Vulkan allocation: NOT BEHAVIORALLY PROVEN by the new unit tests;
- actual descriptor updates: NOT BEHAVIORALLY PROVEN by the new unit tests;
- actual timeline waiting/reuse prevention: NOT BEHAVIORALLY PROVEN by the new unit tests;
- actual destruction cardinality: NOT BEHAVIORALLY PROVEN by the new unit tests.

These operations are statically observed in the extracted owner and protected end-to-end by canonical
validation. No source-text assertion is presented as behavioral evidence.

## Remaining RtComposite ownership domains

This remediation intentionally does not extract frame-sized trace/continuation resources,
Frame Generation resources, presentation resources, or the remaining backend-specific resources.
Those domains require separate authorized remediation. This result does not claim FINAL qualification
or FINAL PASS.
