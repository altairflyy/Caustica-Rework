# REMEDIATION-05B result — TraceFrameResources

Status: COMPLETED

## Ownership transfer

`TraceFrameResources` is the production runtime owner of the complete frame-sized trace and reconstruction working set:

- trace output and continuation queue;
- normal, albedo, depth, motion, specular-albedo and specular-motion guides;
- NRD view-Z, diffuse/specular inputs, diffuse/specular outputs, combined output and validation image;
- the shared display-resolution reconstruction output (`rrOutput`);
- the sizing/configuration key used to decide whether recreation is required.

`RtComposite` retains one `TraceFrameResources` instance and uses immutable borrowed `TraceFrameViews`. It no longer contains a parallel field authority for these resources. `TraceFrameResources` has no dependency on `RtComposite`.

The experimental NRD backend continues to own its combine pipeline and receives borrowed NRD image views from `TraceFrameResources`; it does not own those images. Post-processing continues to own display/HDR images and receives the borrowed reconstruction output.

## Lifecycle preservation and recovered regression

The initial extraction grouped recreation too aggressively and changed the established split resize ordering. `PostProcessingOwnershipTest.productionUsesOnePostOwnerAndPreservesSplitLifecycle()` correctly exposed that production regression.

The owner was therefore split into explicit idle-safe phases while preserving a single authority:

1. release primary trace resources;
2. release ReSTIR resources;
3. release guide resources;
4. release SVGF resources;
5. release reconstruction outputs;
6. create primary trace resources;
7. synchronize ReSTIR resources;
8. create post-processing images;
9. create guide resources;
10. create SVGF resources when enabled;
11. create reconstruction outputs;
12. bind the NRD combine pipeline when enabled;
13. ensure exposure, deliver the temporal reset, rebind world-trace frame views, and bind post-processing.

After the production ordering was restored, the stale structural test was migrated to recognize the new `TraceFrameResources` boundary while retaining the exact split lifecycle assertions. The old direct resource path is not accepted as an alternative.

No new `waitIdle` was introduced. The existing resize-time idle seam remains the authority for immediate destruction. Deferred-retirement policy was not changed. The previously documented exceptional partial-allocation failure limitation remains unchanged.

## Tests and validation

- `TraceFrameResourcesTest`: 4 tests PASS. These cover pure sizing/configuration decisions and the structural ownership/dependency boundary; they do not claim device-level Vulkan behavior.
- `PostProcessingOwnershipTest`: PASS after restoring production lifecycle ordering and migrating only the stale owner-location assumptions.
- `validate-fast.ps1`: PASS; 258 tests, 254 passed, exact 4 canonical failures, 0 unexpected failures, characterization 7/7 PASS.
- `validate-build.ps1`: PASS; V0 + V1 + V2, exact 4 canonical failures, 0 unexpected failures, characterization 7/7 PASS, build PASS.
- `git diff --check`: PASS.

## Test gaps

The unit suite does not behaviorally prove device-level Vulkan allocation, descriptor updates, timeline synchronization, native destruction cardinality, or recovery after a partial native allocation failure. End-to-end regression protection remains the canonical validation suite plus runtime qualification.

## Remaining scope

This remediation does not close FINAL. `FINAL` remains `PENDING`. Significant Frame Generation and presentation-related ownership remains in `RtComposite` and requires separate remediation. This result does not claim that `RtComposite` is globally orchestration-only or that GPU lifetime is globally centralized.
