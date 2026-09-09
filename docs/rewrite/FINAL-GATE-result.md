# FINAL GATE result

## Disposition

Canonical FINAL disposition: `FAIL`.
User-qualified FINAL disposition: `FAIL`.

DH/Voxy and FSR/XeSS remain the exact user-authorized waivers. HDR, NRD,
SHaRC, SVGF direct dispatch, water/glass, LabPBR and ReSTIR runtime
qualification remain NOT TESTED. This result does not relabel any untested or
waived path as runtime qualified.

## Repository-wide integration audit

| Requirement | Status | Evidence |
| --- | --- | --- |
| Required rewrite components are live | PASS | Every added architecture class under `src/main` has a production reference outside its declaration; the targeted integration suite passes. |
| No duplicate migrated ownership | PASS | ReSTIR history, SVGF history, temporal state, SHaRC, LOD lifecycle, TLAS lifetime, deferred deletion, upscaler lifetime and POST each have one owner. Direct-owner searches find no parallel fields in `RtComposite`. |
| Mandatory APIs are present | PASS | Gate result documents plus production/tests cover FrameContext, temporal coordination, neutral LOD, explicit pipeline, GPU/AS boundaries, backend contracts, scene assembly, environment modules and render graph. |
| Development gates removed | PASS | No `RewriteGates` or `engine.*V2` reference exists in `src/main`. |
| Legacy execution unreachable | PASS | `GraphExecution` is the sole cursor authority; `FramePipeline` exposes immutable callbacks only; barrier emitters have no legacy fallback. |
| Composite target | FAIL | `RtComposite` still directly creates, destroys and recreates significant guide, trace, continuation, Frame Generation and presentation resources. The canonical target requires orchestration/delegation only. |
| Owners are univocal | FAIL | Domain-specific migrated owners remain valid, but significant GPU create/destroy/recreate ownership is still split with `RtComposite`; deferred retirement centralization does not make all GPU ownership univocal. |
| Report matches committed code | PASS | `FINAL_REPORT.md` was updated after POST recovery, runner removal, canonical validation and matched benchmark capture. |

The audit found no new shader math/layout change and no new hot-path `waitIdle`.
The one shader delta from the frozen reference is the known `MaterialHeader`
zero-initialization compiler-compatibility fix. GPU lifetime is not fully
centralized: deferred retirement is centralized, but significant resource
create/destroy/recreate ownership remains in `RtComposite`.

## Targeted integration validation

The ownership/integration suite includes composite/POST ownership, legacy-path
removal, graph execution/validation, frame pipeline, temporal state, ReSTIR,
SHaRC, SVGF resources, AS/deferred deletion, scene assembly and the seven
rewrite characterization checks. It passes without failures.

## Matched performance qualification

Reference: `3c54fc201f93598246db62ebb1947dfb1e274e92` plus the minimal current-compiler
`MaterialHeader` initialization. Rewrite: `7b58411`. Both used isolated copies
of the same Nether world, position and settings at 2560x1440 with DLSS-RR/SDR,
render distance 12 and the same disabled optional backends.

| Metric | Reference | Rewrite | Delta | Limit | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Average hardware GPU duration | 62.4092 ms | 61.8635 ms | -0.87% | +5% | PASS |
| P95 hardware GPU duration | 64.6060 ms | 63.9419 ms | -1.03% | +7% | PASS |
| P99 hardware GPU duration | 64.9217 ms | 64.3197 ms | -0.93% | +10% | PASS |
| Average global VRAM | 3971.47 MiB | 4053.28 MiB | +2.06% | +10% | PASS |
| Average CPU envelope | 8.8065 ms | 8.7924 ms | -0.16% | Informational | PASS |
| P95 CPU envelope | 10.046 ms | 10.019 ms | -0.27% | Informational | PASS |
| P99 CPU envelope | 11.415 ms | 11.166 ms | -2.18% | Informational | PASS |

Hardware GPU values use the last 600 of 1088 symmetric Vulkan timestamp samples
per variant around the composite command buffer. The resulting metric is
composite command-buffer GPU duration, not complete frame GPU time. It includes
ray/path tracing, ReSTIR work recorded in the composite, denoiser, upscaler and
composite post work. UI/HUD, presentation/blit, other command buffers, separate
async/build/SDK submissions and Frame Generation are not demonstrated inside
the interval. Temporary probes were removed after capture. Live AS/BLAS deltas over 300 steady frames are bounded: median AS
`+0.45%`, P95 AS `+0.53%`, median BLAS bytes `+0.07%`, P95 BLAS bytes `+0.15%`.

**Composite GPU duration comparison: VERIFIED.**
**Canonical complete GPU frame-time Average/P95/P99: BLOCKED.**

## Functional and safety evidence

- Tested: Overworld, Nether, End, entities, particles/weather, DLSS-RR and SDR.
- Not tested for canonical qualification: water/glass, LabPBR, ReSTIR runtime,
  SHaRC full qualification, SVGF directly proven dispatch, HDR and NRD.
- Waived only: DH, Voxy, FSR and XeSS.
- Canonical four-failure shader baseline and characterization remain exact.
- No new device loss, known UAF, stale cross-dimension geometry or hot-path
  `waitIdle` was introduced by final recovery.
- The known ReSTIR boiling and DH/Voxy provider bug remain unchanged.

## Validation

- Targeted integration/ownership suite: PASS.
- `validate-build.ps1`: PASS; V0 + V1 + V2 in one run.
- V1: 248 tests, exact 4/4 canonical failures and characterization 7/7 PASS.
- V2: Gradle build PASS; NGX shim present at 83,968 bytes.
- `git diff --check`: PASS.
- Gate-8 same-candidate A/B validation: supporting evidence only.
- Matched frozen-reference-vs-rewrite validation: NOT AVAILABLE; this is not a
  separate canonical FINAL requirement row.
- Forbidden-change audit: no executable code, shader/math, ownership,
  synchronization, tuning or `waitIdle` change in the gate-closure diff.

## Final qualification status

Demonstrated FAIL:

- `RtComposite` orchestration-only;
- GPU lifetime centralized;
- `RtComposite` delegation-only integration target.

Blocked:

- complete canonical GPU frame-time Average/P95/P99 coverage.

NOT TESTED:

- water/glass, LabPBR, ReSTIR runtime, SHaRC full qualification, SVGF direct
  dispatch qualification, HDR and NRD.

WAIVED:

- DH, Voxy, FSR, XeSS.
