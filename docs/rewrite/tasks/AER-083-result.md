# AER-083 pre-audit — BLOCKED

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

See BLOCKER-003. This is an unmet prerequisite, not a passing implementation.
No shader, lifetime or synchronization changes were made in this audit. The
installed profile was not modified. Documentation diff check: PASS.
