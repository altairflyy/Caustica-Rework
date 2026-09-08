# AER-080 result

## Runtime seam

`FrameGraph` is instantiated by `RtComposite` from the production
`FramePipeline`. It snapshots the five existing macro-pass names, represents
their four logical hand-offs with `GraphResource` and `GraphAccess`, computes a
stable topological order, and rejects an order that differs from the declared
pipeline.

The graph is shadow-only: it exposes no execution method, records no Vulkan
stage/layout/access metadata, owns no GPU resource, and does not replace the
legacy `FramePipeline.Cursor` execution path.

## Preserved behavior

- The production order remains `PrepareFramePass`, `PathTracePass`,
  `ReconstructionPass`, `UpscalePass`, `PostPresentPass`.
- Frame execution remains exclusively in `FramePipeline` and its cursor.
- Resource ownership, synchronization, queue submission and pass timing are
  unchanged.
- AER-081 declarations and AER-082 graph execution were not started.

## Validation

- Targeted `FrameGraphTest` and `FramePipelineTest`: PASS.
- `validate-build.ps1`: PASS, covering V0, V1 and V2.
- V1: 207 tests, exact 4/4 canonical baseline failures; characterization 7/7 PASS.
- V2: Gradle build PASS.
- Forbidden-change audit: no shader, shader ABI/math, Vulkan synchronization,
  GPU ownership, queue submission or hot-path `waitIdle` changes.
