# GATE-8 result

## Scope

Integration audit of AER-080 through AER-084. The final candidate is
`caustica-0.2.8-gate8-candidate.jar`, SHA-256
`D6B31873072FDEBA92F71621E087BEEC0A6183EA6346E941B350C3FEAEC7FD9A`.

## Task compliance

| AER | Status | Production evidence |
| --- | --- | --- |
| AER-080 | PASS | `RtComposite` constructs `FrameGraph.shadow(framePipeline)` and verifies its topological order against the five production callbacks. |
| AER-081 | PASS | Every macro-pass has immutable resource use declarations with mode, queue, stage and layout; graph construction runs read-before-write, unordered-writer and undeclared-resource diagnostics. |
| AER-082 | PASS | `GraphExecution` executes production callbacks in graph order behind `engine.renderGraphV2`; selection is once per frame and the legacy cursor remains the A path. |
| AER-083 | PASS | POST, denoiser, available upscaler backends and path-trace outputs replace their matching legacy barrier only when their independent gate is enabled. Each available family completed same-JAR Vulkan A/B. |
| AER-084 | PASS | Production `RtGpuExecutor` delegates logical build/publication/graphics timeline values to `QueueDependencyScheduler` while applying the same Vulkan wait/signal at the same boundaries. |

## Integration completeness

| Requirement | Status | Evidence |
| --- | --- | --- |
| Pass order semantics | PASS | Graph and legacy traces use `Prepare`, `PathTrace`, `Reconstruction`, `Upscale`, `PostPresent` with identical incremental callback/failure semantics. |
| Resource declaration coverage | PASS | Aggregate graph declarations diagnose structural errors; per-family barrier plans refine actual images/operations before synthesis. |
| Automatic barrier authority | PASS | Each enabled family chooses generated versus legacy in one emitter; no callback records both paths for the same boundary. |
| Queue/timeline dependencies | PASS | Published compute transfer/build work waits on `buildTimeline` at `beginGraphicsUse`; final graphics use signals `graphicsTimeline` for reuse/retirement. Token order, stage masks and overlap match the reference. |
| Runtime adoption | PASS | Graph execution, barrier emitters and queue scheduler are all referenced by `src/main`; no test-only facade remains. |
| Forbidden scope | PASS | No AS-first synthesis, shader/math/ABI change, transient aliasing, new physical queue, new submit or hot-path `waitIdle`. |

## Vulkan runtime qualification

The final generated-path smoke used DLSS-RR, all generated-family flags and
synchronization validation. It exercised first frame/reset, stationary operation,
movement/rotation, menu, world reload and shutdown. Required graph, path-trace,
DLSS-RR, upscaler and POST markers were present.

Evidence directory:

`build/rewrite-validation/GATE-8-validation/B-0cec8f8c76684ad48756798e4abbca31`

The finding set exactly matches the accepted AER-083 baseline:

- 4 `VUID-VkGraphicsPipelineCreateInfo-Input-08733` messages from the known DH path;
- 2 `SYNC-HAZARD-WRITE-AFTER-WRITE` messages from the known NGX DLSSD path;
- 2 occurrences of `VUID-vkDestroyDevice-device-05137`, representing the same
  single known shutdown child-object leak report.

No new VUID, synchronization hazard, device loss or profiler failure appeared.

## Performance qualification

Validation was disabled for timing. A and B used the same JAR, profile, world,
DLSS-RR backend, disabled frame generation and final 600 active RT frames after
the user-performed warm-up. Unequal loading/menu portions were excluded.

| Metric | Legacy A | Graph/generated B | Delta | Gate |
| --- | ---: | ---: | ---: | --- |
| Average CPU frame envelope | 7.3314 ms | 8.3637 ms | +14.08% | Informational; not a GPU-average claim |
| P95 frame envelope | 9.214 ms | 9.699 ms | +5.26% | PASS |
| P99 frame envelope | 10.472 ms | 11.207 ms | +7.02% | PASS, below +10% threshold |

Evidence directories:

- A: `build/rewrite-validation/GATE-8-performance/A-434578dd0a7b4bfb8110c4379071fc17`
- B: `build/rewrite-validation/GATE-8-performance/B-f178a7f8e8864419a87d2c470a196201`

The average value is retained transparently but is a CPU-side profiled envelope,
not the roadmap's GPU frame-time metric. GATE-8 explicitly gates P99, which passes.

## Validation and recovery

- Targeted graph, barrier, queue scheduling and frame-stat registration tests: PASS.
- `validate-build.ps1`: V0/V1/V2 PASS; V1 exact 4/4 canonical failures and
  characterization 7/7 PASS.
- The first timing attempt exposed the pre-existing missing
  `terrain.lightGridPublish` profiler registration. Commit `78a46c7` registered
  all existing literal stage/counter names and added a source-to-registry test.
  The crashed attempt was discarded; both accepted runs use the corrected hash.
- Full diff/forbidden-change audit across GATE-8: PASS.

## Conclusion

`GATE-8 integration completeness: PASS`.

`GATE-8 overall: PASS`.

AER-090 is READY and was not started during gate closure.
