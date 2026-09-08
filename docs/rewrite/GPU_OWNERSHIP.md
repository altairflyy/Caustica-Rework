# GPU resource ownership

This document records the production ownership and lifetime rules observed
after AER-046. It is an audit of the current implementation, not a target design.

## Lifetime mechanisms

- `RtGpuExecutor.Build` identifies async-compute build completion. A build that
  becomes visible is passed to `markPublished(...)`, and the next graphics use
  attaches the corresponding compute-timeline wait.
- `RtGpuExecutor.GraphicsUse` identifies one graphics submission. Published
  replacement resources are retired through `DeferredDeletionQueue` with the
  exact last-use token.
- `TrackedGraphicsUse` is embedded in reusable rings/caches and records the
  greatest graphics token that used that slot. Reuse waits through one
  `GraphicsUseWaiter` completion snapshot; retirement uses the tracked token.
- Never-published build results use `retireUnpublished(...)` or are destroyed by
  a build-completion callback after the compute token has completed.
- Resize/reload/device-teardown paths that destroy graphics resources directly
  first establish device-idle or an equivalent completed-token condition.

## Ownership matrix

| Resource | Owner | Creator | Last-use tracker | Destroy path | Queue | Recreation trigger |
| --- | --- | --- | --- | --- | --- | --- |
| Generic `RtBuffer` allocation | The subsystem field/record holding the buffer; `RtBuffer` owns only its VMA handle/allocation | `RtContext.createBuffer`, aligned/async variants, or `createUploadBuffer` | Chosen by the logical owner: `GraphicsUse`, `TrackedGraphicsUse`, build completion, or device-idle | Logical owner calls `RtBuffer.destroy()` directly only after safety is established, or supplies it to `DeferredDeletionQueue` / `retireUnpublished` | Graphics, async compute, or both according to the factory variant | Owner-specific capacity, extent, content generation, feature toggle, reload, or teardown |
| Generic `RtImage` allocation | The subsystem field/container holding the image; `RtImage` owns its Vulkan image, view, and VMA allocation | `RtContext.createStorageImage` or `createTransientMsaaColorImage` | Graphics frame completion; the critical frame-image resize paths audited below use device-idle rather than an individual token | Owner calls `RtImage.destroy()` only after its subsystem has established the required completion condition | Graphics queue; initialization layout transition uses synchronous graphics submission | Extent/format/feature change, reload, or teardown |
| Path-trace color, guide images, display images, continuation queue, and `rrOutput` | `RtComposite` | `RtComposite.ensureOutput(...)` through `RtContext` | Whole-frame graphics submission; descriptors live in the world/display pipelines | `ensureOutput(...)` waits device-idle, destroys the old set, then allocates/rebinds; `RtComposite.destroy()` runs under teardown-idle | Graphics | Display/render extent, selected reconstruction/upscaler mode or quality, denoiser availability, teardown |
| ReSTIR reservoir pair | `RestirHistory` | `RestirHistory.ensure(...)` via `RtContext.createBuffer` | Whole-frame graphics submission; ping-pong parity is owned by `RestirHistory` | `ensure(...)` waits device-idle before `destroy()` and recreation; teardown calls `destroy()` after idle | Graphics ray tracing | Render extent or ReSTIR enable state |
| SVGF history, moments, filter, previous view-Z and previous-normal images | `SvgfResources` | `SvgfResources.allocate(...)` via `RtContext.createStorageImage` | Whole-frame graphics submission; history parity/validity remains in the same owner | `RtComposite.ensureOutput(...)` establishes device-idle, then `SvgfResources.destroy()`; teardown is also idle | Graphics compute/ray-trace frame | Render extent, denoiser selection/availability, teardown |
| SHaRC cache buffer | `SharcRadianceCache` is the host owner; `RtSharc` retains the allocation implementation | `RtSharc.rebuild(...)` via `RtContext.createBuffer` | Whole-frame graphics trace; no separate token is stored | Resize, disable, and teardown use `RtSharc.destroyNow/rebuild` after `RtContext.waitIdle()`; reset uses synchronous fill | Graphics | Entry-count change or disable/re-enable; explicit reset and world/dimension invalidation clear contents without replacing the allocation; teardown |
| Vanilla terrain section BLAS, AS backing, UV/material buffers | `RtTerrain` resident map through `RtSectionTable.SectionGeom`; `PreparedSection` owns resources until publication; `AccelerationStructureManager` is the AS operation/lifetime boundary | `RtSectionBuilder.prepare(...)` through `AccelerationStructureManager.prepareStaticBlas(...)`, delegating to `RtAccel` | Async `Build` until publication; replaced resident generations use `latestGraphicsUse()` | Manager releases scratch/AS; stale never-published results retain `retireUnpublished`; published geometry retires through manager to `DeferredDeletionQueue` | Async compute build, then graphics trace | Chunk publication/rebuild/eviction, material or terrain epoch, world reset, shutdown |
| Terrain section-table buffer generation | `RtSectionTable` | `RtSectionTable.acquireGeneration(...)` via `RtContext.createBuffer` | The `GraphicsUse` captured before table generation replacement | Retired through `DeferredDeletionQueue` into the recycled-generation pool; undersized recycled buffers use `retireUnpublished`; shutdown destroys after idle | Graphics, host-mapped writes | Required slot capacity, publication generation, world reset, shutdown |
| LOD BLAS, backing, build scratch, UV/material buffers | `RtLodTerrain.BuildSession` owns new geometry until checkpoint/final publication; ownership then transfers to the published `Proxy`; manager is the AS boundary | `RtSectionBuilder.prepare(...)` through the manager, with optional manager-delegated compaction | Async `Build` before publication; replaced `Proxy` uses `latestGraphicsUse()` | Manager releases scratch, compaction objects and AS; replaced/cleared proxies retire through manager/`DeferredDeletionQueue`, excluding retained geometry | Async compute build/compaction, then graphics trace | Provider revision/selection, progressive checkpoint/final replacement, retry/reset, shutdown |
| LOD proxy table buffer | Published `RtLodTerrain.Proxy`; temporary owner is `publishBuildSession(...)` until construction succeeds | `RtLodTerrain.publishBuildSession(...)` via `RtContext.createBuffer` | Same last graphics-use token as the replaced proxy | Failed publication destroys immediately before visibility; replaced/cleared proxy destroys through `DeferredDeletionQueue`; shutdown follows idle | Graphics, host-mapped writes | Progressive checkpoint, final publication, provider reset, shutdown |
| Block-entity BLAS/backing/packed geometry | `RtEntities.BeEntry`; manager is the AS/backing boundary | `RtEntities` through manager static-build delegation and `RtContext` geometry buffers | Per-entry `TrackedGraphicsUse`, marked by `RtEntities.markGraphicsUse(...)` | `retireBe(...)` submits the exact tracked token through manager/`DeferredDeletionQueue`; manager destroys AS/backing in the callback | Graphics build/trace | Mesh hash change, eviction/unload, entity reset, teardown |
| Dynamic-entity BLAS/backing/geometry/refit scratch | One `RtEntities.EntitySlot` in the per-entity ring; manager is the AS/backing/scratch boundary | `RtEntities` through manager static/updatable/refit delegation and buffer factories | Per-slot `TrackedGraphicsUse`, marked after successful frame enqueue | Slot reuse waits for tracked completion; eviction uses manager retirement; replacement and idle teardown destroy through manager | Graphics build/refit and trace | Ring advance, topology/capacity change, stale entity eviction, teardown |
| Entity geometry-table ring and transient frame buffers | `RtEntities.TableSlot` and `FrameLists` rings | `RtEntities` via `RtContext.createBuffer` | Per-table/per-frame-list `TrackedGraphicsUse` | Ring reuse waits for completion; table replacement retires each old slot through `DeferredDeletionQueue`; teardown destroys after idle | Graphics | Capacity growth, ring reuse, teardown |
| Frame TLAS, AS backing, instance buffer, scratch | `RtAccel.TlasRing`, held by `RtComposite` | Manager delegates frame build/recording to `RtAccel.prepareTlas/createTlasSlot` | Per-slot `TrackedGraphicsUse`; marked with the frame token and awaited before overwrite/resize | `TlasRing` performs its internal completed-slot cleanup and idle teardown; this is the documented AS-manager exception because the ring owns all slot resources as one legacy aggregate | Graphics build and ray tracing | Ring advance, instance-capacity growth, teardown |
| World RT pipeline, descriptor sets, SBT, descriptor-use ring | `RtPipeline`, referenced by `RtComposite` | `RtPipeline.create(...)` | Descriptor slots use `TrackedGraphicsUse` and `GraphicsUseWaiter`; pipeline-wide destruction uses idle | Resource reload waits idle before pipeline destruction/recreation; device teardown is idle | Graphics ray tracing | Resource reload, shader/pipeline recreation, teardown |
| Upscaler/reconstruction output image | `RtComposite.rrOutput`; DLSS-RR, FSR, XeSS, SVGF/native fallback only borrow it as an output target | `RtComposite.ensureOutput(...)` via `RtContext.createStorageImage` | Whole-frame graphics submission | Destroyed/recreated only after `ensureOutput(...)` device-idle; teardown also occurs after idle | Graphics | Display extent or reconstruction/upscaler configuration change, teardown |
| DLSS-RR, FSR, and XeSS internal feature/context resources | Respective singleton backend (`RtDlssRr`, `RtFsrUpscaler`, `RtXessUpscaler`); shared native runtime ownership remains in its runtime singleton | Backend `ensureFeature(...)` / native shim | Backend submission uses the same frame command buffer; release paths establish device-idle | Backend-specific release/destroy; caller-provided `rrOutput` is not owned or destroyed by the backend | Graphics/native backend | Display/render extent, quality/preset, enable switch, backend failure, device teardown |
| Overlay per-frame upload buffers | `RtOverlayFramePool` until `endFrame(...)` captures an immutable retirement list | `RtOverlayFramePool.acquire(...)` via `RtContext.createBuffer` | Exact frame `GraphicsUse` passed to `endFrame(...)` | Entire acquired list retires through `DeferredDeletionQueue`; teardown destroys only buffers still locally held after idle | Graphics | Every overlay frame, overlay shutdown |

## Ownership transfers and borrowed handles

- A terrain/LOD `PreparedSection` owns every allocation until publication. On
  successful publication, its BLAS plus UV/material buffers move into a
  `SectionGeom`; upload/build-input/scratch allocations do not transfer.
- A progressive LOD checkpoint transfers `BuildSession.owned` geometry to the
  new `Proxy`; retained geometry is excluded when the prior proxy retires.
- `RtComposite` and backends borrow image handles during dispatch. In
  particular, upscalers never own the caller-provided `rrOutput`.
- Minecraft atlas/backbuffer/native render-target handles bound or copied by
  Caustica are borrowed external resources and are not destroyed by these RT
  owners.
- `DeferredDeletionQueue` owns no resource and no timeline state. It forwards
  retirement to `RtGpuExecutor`, which remains the single destroy-queue and
  graphics/build-timeline authority.
- `AccelerationStructureManager` owns no Vulkan allocation. It is the runtime
  operation and destruction boundary for migrated LOD, terrain and entity AS;
  `RtAccel` retains Vulkan implementation and the `TlasRing` aggregate.

## Runtime diagnostics

- `RtAccel.liveCount()` counts all live BLAS and TLAS handles at their common
  constructor/idempotent-destroy seam.
- `RtAccel.liveBlasBytes()` counts backing bytes for live bottom-level AS.
- `DeferredDeletionQueue.pendingRetirements()` reports published-resource
  retirement jobs held by the executor.
- `DeferredDeletionQueue.queueDepth()` reports the complete executor destroy
  queue, including unpublished jobs.
- `AccelerationStructureManager.recordDiagnostics(...)` samples these values
  once per frame into the opt-in `RtFrameStats` CSV/hitch output.
- Counter decrement asserts against underflow. These counters observe lifetime;
  they do not schedule, delay or execute destruction.

## Audit conclusion

Every critical resource family required by AER-046 has a declared production
owner, creation point, last-use mechanism, destruction path, queue domain, and
recreation trigger. The audit identifies no ownerless critical allocation and
does not authorize changing any lifetime behavior.
