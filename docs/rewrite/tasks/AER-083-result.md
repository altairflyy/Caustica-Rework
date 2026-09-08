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

## Denoiser-family candidate

The denoiser candidate uses the separate `engine.denoiserBarriersV2` flag,
effective only with `engine.renderGraphV2=true` and OFF by default. POST selection
is independent and the denoiser A/B harness explicitly keeps POST generated
barriers disabled.

SVGF declarations identify current source/motion/viewZ/normal/albedo, previous
history/moments/viewZ/normal, current history/moments, both filter images and the
final reconstructed output. Ping/pong names follow the existing `writeToPing`
parity. Previous resources are imported cross-frame read-only inputs; their
opposite halves and previous guides are current-frame outputs. Operations cover
reproject, every a-trous pass, history feedback copy, previous-guide copies and
export. NRD declarations cover opaque external outputs, combine and downstream
export. No SDK-internal barrier is claimed.

The legacy branch emits barriers at the same command boundaries as before. The
generated branch emits only compiled hazards and retains the same conservative
stage/access scope. Targeted denoiser, SVGF-resource and retained POST tests PASS.
`validate-build.ps1`: PASS; V1 230 tests with exact 4/4 canonical failures and
characterization 7/7; V2 PASS.

Runtime same-JAR SVGF A/B: PASS. The accepted pair used SHA-256
`986CC87A559409A63B68A7AFC2AF16E36E7FBCFC298D036808B63D2A6E95C464`, with
upscalers disabled, SVGF enabled and synchronization validation active. Run A
recorded the `legacy`/SVGF marker; run B recorded the `generated`/SVGF marker.
Neither run reported a `SYNC-HAZARD`. Both reported only the two established DH
vertex-input occurrences of `VUID-VkGraphicsPipelineCreateInfo-Input-08733` in
the common finding set. Legacy A additionally reported the intermittent known
`VUID-vkDestroyDevice-device-05137` shutdown leak; generated B did not, so B
introduced no new validation finding. The user completed matched first-frame,
stationary accumulation, movement/camera, menu and world-reload checks without
reporting a visual or temporal regression. NRD runtime qualification is
`NOT AVAILABLE`: the quarantined backend is not selectable in this build.

Accepted evidence directories:

- legacy A: `build/rewrite-validation/AER-083-denoiser/A-609a70c69cd74350ae768f06d8899d8e`
- generated B: `build/rewrite-validation/AER-083-denoiser/B-98fd42ce3d23455da39a0c62543d378a`

The denoiser family is qualified. AER-083 remains ACTIVE; upscaler and path-trace
output families are intentionally untouched and remain to be qualified before
task closure.

## Upscaler-family candidate

Runtime seams audited: NativeUpscalerBackend pre-blit and RtComposite upscale
export to exposure. Replace those two legacy calls behind the independent,
default-OFF engine.upscalerBarriersV2 gate (requires renderGraphV2). The native
producer is TRANSFER, SDK producers are EXTERNAL, downstream exposure is COMPUTE.
Per-image declarations distinguish current color, depth/motion, DLSS guides and
the distinct rrOutput. SDK-owned cross-frame histories/layouts remain opaque and
unchanged; incoming trace/denoiser barriers retain their original ownership.
Actual successful producer selection drives export declarations and runtime
markers, including native fallback. Generated barriers retain legacy stage/access
scope and command boundaries. POST/denoiser implementations remain unchanged.

Validation: targeted UpscalerBarrierPlan/NativeUpscalerBackend and retained
POST/denoiser tests PASS; validate-build.ps1 PASS (234 tests, exact 4/4 canonical
failures, characterization 7/7, V2 PASS). V0 and forbidden-change review PASS:
no shader math, trace-output barriers, SDK algorithms, reset/jitter changes or
new waitIdle. A/B harness syntax checked. Same-JAR runtime qualification PENDING.
FSR/XeSS native binaries are unavailable in this build; their runtime status is
NOT AVAILABLE. NATIVE and DLSS_RR require separate matched A/B pairs, with
backend selection fixed by JVM properties and synchronization validation active.
See AER-083-upscaler-AB.md. AER-083 stays ACTIVE; AER-084 remains PENDING.

Native upscaler same-JAR A/B: PASS. Both runs used SHA-256
`7FBF139FA5E34E15A5E54FE9886D532958805B576DFA092748589F5C6B9BC78D`,
reported the requested `NATIVE` backend and exercised respectively the `legacy`
and `generated` paths with synchronization validation active. Neither run
reported a `SYNC-HAZARD`; their normalized validation findings match exactly:
two established DH `VUID-VkGraphicsPipelineCreateInfo-Input-08733` occurrences
and the known intermittent `VUID-vkDestroyDevice-device-05137` shutdown leak.
The user completed the matched first-frame, stationary accumulation,
movement/camera, menu and reload route without reporting a visual regression.

Accepted native evidence directories:

- legacy A: `build/rewrite-validation/AER-083-upscaler/A-c7fff887474642ecab930dc138b6d4ff`
- generated B: `build/rewrite-validation/AER-083-upscaler/B-df6471d85aa2415bb9b33703dc11bdb0`

Native is qualified. DLSS-RR runtime qualification remains pending.
