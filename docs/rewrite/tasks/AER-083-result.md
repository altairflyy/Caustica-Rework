# AER-083 progress — ACTIVE

AER-082 completed in `6fef5fb`; its worktree was clean before this audit.
No automatic barrier implementation has been added or enabled. AER-084 has not
started. GATE-8 remains PENDING.

## Production audit

The first family must be post images. `RtComposite.postPresentFrame` records
exposure, a legacy barrier, display mapping, another barrier, display-to-main
transfer, and the final barrier. `RtExposure.record` also contains histogram
clear / histogram dispatch / resolve dependencies. Manual exposure clears its
image through transfer. These operations must be accounted for before replacing
any broad legacy barrier.

`FrameResourceDeclarations` describes aggregate macro-pass families and opaque
backend accesses. It cannot prove per-image/per-branch hazards. The post
declarations omit separate histogram/state buffers and manual-exposure transfer
stage. Refining these declarations is necessary within AER-083. A passing shadow
diagnostic is not synchronization qualification.

## Qualification gap

- The repository has validate-fast/build, but no scripted world replay,
  A/B synchronization-validation scenario or P99 measurement harness was found.
- Modrinth `prova` still contains the active `caustica-0.2.7.jar`.
- Its latest.log was last written on 2026-09-08 at 17:48:32 and enumerates the
  Khronos validation layer. Enumeration does not establish active synchronization
  validation; this log also predates the graph candidate.
- The SDK contains VkLayer_khronos_validation.dll/json: the layer is installed.
  No Minecraft javaw process was running during the audit.
- This session has no graphical desktop-control surface or configured scenario
  runner for autonomously executing and observing the required world scenarios.
- Current-build Vulkan A/B, generated-barrier validation and P99: NOT TESTED.

## Recovery order

1. Establish repeatable runs on a preserved test profile: world load/reload,
   movement, HDR/SDR, proof of active synchronization validation, candidate hash,
   flag values and retained logs. Human-assisted execution or a tested scenario
   harness is needed to collect this evidence.
2. Refine post declarations to concrete images, ordered operations, layouts and
   access/stage masks. Implement the first-family candidate with explicit A/B.
3. Run targeted tests and validate-build, then qualify explicit versus generated
   barriers on the same candidate. Only after PASS continue to denoiser images,
   upscaler images and path-trace outputs, including fallback/history cases.
4. Complete and commit AER-083 before AER-084. Gate closure also requires Vulkan
   error comparison, complete resource coverage and comparable P99 evidence.

See BLOCKER-003. This pre-audit originally identified the runtime prerequisite;
the first-family results below resolve that portion while the task stays ACTIVE.

## Post-image family candidate

The first family now has a same-JAR A/B implementation. The generated path is
selected by `engine.renderGraphV2=true` plus `engine.postBarriersV2=true`; the
legacy path remains the default. Concrete operations cover histogram clear,
histogram dispatch, exposure resolve/manual clear, display mapping, display copy
and final export. The candidate emits a barrier only for a compiled hazard while
retaining the exact broad stage/access scope of Minecraft 26.2's legacy barrier.

Runtime A/B used candidate SHA-256
`41F3BC827A451F2646B4305F4AE6FC0A737C0596F1FD6155B280BD16DFD82646`.
Both runs enabled the graph and explicit Khronos synchronization validation.
The logs prove `legacy` for A and `generated` for B, including automatic and
manual exposure with HDR disabled.

The validation findings are baseline-equivalent across A and B:

- two `VUID-VkGraphicsPipelineCreateInfo-Input-08733` messages from the known
  Distant Horizons vertex-input incompatibility;
- two identical `SYNC-HAZARD-WRITE-AFTER-WRITE` reports on named NGX DLSSD
  internal resources;
- one identical `VUID-vkDestroyDevice-device-05137` report with 49 objects at
  shutdown.

No new VUID or synchronization hazard appeared on B. The user reports visual
equivalence and successful operation for both runs. HDR was not exercised and is
recorded as `NOT TESTED`, not PASS. Post SDR automatic/manual A/B: PASS.

Static validation for this candidate: targeted barrier/graph tests PASS;
`validate-build.ps1` PASS with 224 tests, exact 4/4 canonical failures and
characterization 7/7; V2 PASS. The preflight also proved the settings file enables
Core Checks and Synchronization. AER-083 remains ACTIVE while the denoiser,
upscaler and path-trace output families are implemented and qualified.
