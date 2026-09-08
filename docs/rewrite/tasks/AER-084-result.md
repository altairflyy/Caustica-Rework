# AER-084 result

## Pre-audit and runtime seam

The reference has two physical Caustica lanes: frame work is deferred into
Minecraft's graphics submission, while asynchronous transfer and acceleration-
structure build work is batched on the reserved compute queue. `RtGpuExecutor`
already joins published build output to graphics with `buildTimeline`, and tracks
the final graphics consumer with `graphicsTimeline` for reuse and retirement.

`QueueDependencyScheduler` is now the single authority for the logical values and
dependencies of those two timelines. `RtGpuExecutor` instantiates it in production
and applies its schedule at the existing boundaries. The executor continues to own
the Vulkan queues, semaphores, command buffers, submit, host waits and destruction
processing.

## Integrated schedule

- Each asynchronous transfer/build job reserves the same monotonic build value at
  enqueue time.
- The existing compute-queue batch signals the newest included build value, then
  records that exact submitted value in the scheduler.
- Publication coalesces to the newest published build. At `beginGraphicsUse`, the
  scheduler emits the `COMPUTE / TRANSFER_BUILD -> GRAPHICS` build-timeline wait.
- The wait is still attached only after the future timeline value has reached an
  actual queue submission. A failed host-side submission wait does not consume a
  graphics completion value.
- `endGraphicsUse` signals the same graphics timeline after the final legacy
  consumer and only then records the completed frame token used by resource reuse
  and deferred retirement.

The scheduler owns no Vulkan handle or GPU resource. It creates no command buffer,
submission, barrier or host wait. Unpublished asynchronous work remains overlapped
with graphics, published work waits at the same frame boundary, and repeated waits
on an already completed published value preserve the reference behavior.

## Validation

- Targeted `QueueDependencySchedulerTest`, `GraphExecutionTest` and
  `FrameGraphTest`: PASS.
- Scheduling tests cover monotonic values, submitted-value batching, publication
  coalescing, unpublished overlap, retained published dependencies, graphics
  completion timing and invalid tokens. Source review confirms the graphics token
  is still reserved only after the submission wait succeeds.
- Development V1: PASS; 245 tests with exact 4/4 canonical failures and
  characterization 7/7 PASS.
- Closure `validate-build.ps1`: PASS. V1 ran 245 tests with exact 4/4
  canonical failures and characterization 7/7 PASS; V2 build PASS.
- Diff and forbidden-change audit: no shader/math, barrier/stage mask, queue submit,
  semaphore creation, resource ownership, transient aliasing or hot-path
  `waitIdle` change. No new physical queue or synchronization edge was introduced.

AER-084 is DONE. GATE-8 remains pending its separate integration audit and gate
acceptance; AER-090 has not started.
