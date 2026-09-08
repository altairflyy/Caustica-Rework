# AER-081 result

## Pre-audit and seam

The runtime constructs `FrameGraph.shadow(framePipeline)` in `RtComposite`.
The five callbacks execute incrementally; NRD/combine remains between the trace
and reconstruction callbacks. Backend selection, fallback and image aliases
remain runtime decisions. Declarations therefore describe conservative macro-pass
resource families, not a physical image/subresource schedule.

`GraphPass.resources` now stores ordered immutable `GraphResourceUse` entries:
READ, WRITE or READ_WRITE, queue, stage set and layout expectation. The existing
`GraphAccess` remains the ordering edge from AER-080. `FrameGraph` creates the
production declarations and diagnoses them during its existing initialization;
invalid declarations report their pass/resource and prevent accepting that graph.

## Production mapping

| Pass | Declared resource families | Queue / stages |
| --- | --- | --- |
| Prepare | Frame invocation read; temporal snapshot/jitter write | Host |
| Path trace | Scene/AS/materials read; tracer history read/write; output/guides write | Graphics / ray tracing |
| Reconstruction | Trace/guides and external NRD result read; backend state read/write; optional rrOutput write; selected source/result publication | Graphics / compute, transfer, backend-managed; host result |
| Upscale | Selected source, trace/guides, reconstruction and NRD outputs read; backend state read/write; rrOutput publication | Graphics / compute, transfer, backend-managed; host input |
| Post/present | rrOutput read; exposure read/write; display write then transfer read; main target write | Graphics / compute and transfer |

GENERAL describes exposed image boundaries, NOT_APPLICABLE host/buffer families,
and BACKEND_MANAGED opaque SDK/internal resources. Imported histories and backend
state remain initialized/reset by their existing owners. NRD is explicitly an
external input because its work is not inside these callbacks.

Declarations are a conservative union of optional accesses. In particular RR may
write rrOutput in reconstruction, while upscale guarantees its availability through
the RR result or a temporal/native fallback. Ordered writers are legal. These
declarations do not prove per-branch initialization, per-image aliasing or SDK
internal layouts, and must not be used for automatic barrier synthesis without
refinement. No barriers, submissions, image bindings or execution paths change.

## Diagnostics and tests

- Read-before-write requires an imported input, an earlier local write, or a writer
  reachable through declared dependencies. A future or unordered writer is insufficient.
- Multiple writer ambiguity means writers without a dependency path in either
  direction; topological tie-breaking does not hide ambiguity.
- Undeclared resource identifies a pass access absent from the resource registry.
- Targeted graph/pipeline tests verify the production order and declarations,
  all three diagnostics, transitive ordering, read/write input requirements,
  imported histories, local write/read order and immutable metadata.

## Validation

- Targeted `GraphValidationTest`, `FrameGraphTest`, `FramePipelineTest`: PASS.
- `validate-build.ps1`: V0 + V1 + V2 PASS.
- V1: 215 tests; exact 4/4 canonical baseline failures; characterization 7/7 PASS.
- V2: Gradle build PASS. Existing missing optional FSR/NRD/XeSS native bundles
  remain reported by the build; no backend was disabled for validation.
- V3: NOT TESTED in this task; no new runtime smoke is claimed.
- Full diff review and `git diff --check`: PASS.
- Forbidden-change audit: only graph declarations/diagnostics, tests and task
  documents changed. No shader/math, Vulkan barriers, resource ownership,
  queue submission, configuration or hot-path waitIdle changes.
- AER-081 DONE; AER-082 READY and not started.
