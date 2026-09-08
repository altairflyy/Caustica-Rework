# AER-082 result

## Runtime integration

`RtComposite` binds `GraphExecution` once to its existing five callbacks. The
executor resolves those callbacks in `FrameGraph.topologicalOrder`, and advances
one callback per existing recording boundary through `FrameCursor`.

`engine.renderGraphV2=false` (default) returns the existing `FramePipeline.Cursor`.
`-Dengine.renderGraphV2=true` returns the graph cursor. Selection is sampled once
at frame begin. Both paths are present in the same build.

The graph cursor invokes the callbacks directly in the compiled graph order.
It does not forward execution to the legacy cursor when enabled. No callback
body, barrier, transition, queue submission, temporal calculation or resource
ownership changed. NRD and all intervening recording remain at their original
call sites. Advancement before invocation and exception propagation match legacy.

## Validation

- Targeted GraphExecutionTest, FrameGraphTest, FramePipelineTest: PASS.
- A/B tests compare callback traces, interleaved operations and frame identity;
  inject a failure at every callback and verify propagation, advancement,
  completion and exhausted-cursor behavior on both paths.
- Feature selection is verified for both values and remains fixed for a cursor.
- validate-build.ps1 V0/V1/V2: PASS.
- V1: 217 tests, exact 4/4 canonical baseline failures, characterization 7/7 PASS.
- V2: build PASS; existing optional native bundle limitations unchanged.
- V3 and visual/GPU A/B: NOT TESTED; unit A/B is not Vulkan runtime evidence.
- Full diff review and forbidden-change audit: no shader/math, callback-body,
  barriers, transitions, queue scheduling, lifetime or waitIdle changes.
- AER-082 DONE. AER-083 becomes READY only after this task's atomic commit.
