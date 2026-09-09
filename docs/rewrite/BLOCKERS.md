# Rewrite Blockers

## BLOCKER-003 — GATE-8 automatic-barrier qualification

**Status:** CLOSED

**Resolution:** AER-083 qualified POST, denoiser, every available upscaler path
and path-trace outputs with same-JAR A/B plus synchronization validation. AER-084
integrated the existing compute/transfer-build and graphics timeline schedule
without adding a submit, wait, semaphore, hot-path allocation or `waitIdle`.

The final `0.2.8` candidate reproduced exactly the accepted validation finding
set. A matched 600-active-frame performance window measured P99 `10.472 ms` for
legacy A and `11.207 ms` for graph/generated B, a `+7.02%` delta within the
roadmap's `+10%` gate threshold. See `GATE-8-result.md` for the complete audit.

## BLOCKER-001 — GATE-0 baseline characterization

**Status:** CLOSED

**Blocks:**

- AER-004
- GATE-0
- AER-010 and all tasks depending on GATE-0

## Current situation

The validated Caustica reference is:

```text
reference/validated-caustica-0.2.0
3c54fc201f93598246db62ebb1947dfb1e274e92
```

The project builds and runs on the target Windows machine.

DLSS-RR runtime initialization has been validated successfully using the NGX shim rebuilt from source.

The previous sandbox/network limitation is no longer relevant.

## Test baseline

The validated reference test suite completed with:

```text
123 tests
114 passed
9 failed
```

Historical observation:

The reference run reported 9 failures. Five were characterization-harness
artifacts caused by `slice()` assuming LF on a Windows CRLF checkout:

### RtParallaxShaderRegressionTest

- `sideWallsReplaceTheMappedNormalOnBothHitPaths`
- `blockSpritesTileWhileEntityAtlasesStopAtTheirIsland`
- `crossingBudgetFromJavaStaysInsideTheShaderBounds`
- `columnHeightsAreTexelExactAndStayInsideTheSprite`
- `everyHeightSampleIsBoundedByTheCrossingBudget`
- `reliefDepthStillCollapsesWhenPomIsDisabled`
- `heightFieldIsWalkedAsAColumnGridNotAsDepthLayers`

### RtWaterWaveShaderRegressionTest

- `continuationOriginsStayOffTheRestPlaneMesh`
- `animatedWaterIntersectsTheHeightFieldAlongTheViewRay`

The five EOL-dependent failures are resolved as:
`RESOLVED — characterization harness / EOL artifact`.

The canonical baseline retains only these four genuine failures:

- `sideWallsReplaceTheMappedNormalOnBothHitPaths`
- `blockSpritesTileWhileEntityAtlasesStopAtTheirIsland`
- `continuationOriginsStayOffTheRestPlaneMesh`
- `animatedWaterIntersectsTheHeightFieldAlongTheViewRay`

The harness correction changed no renderer or shader behavior.

## AER-004 characterization test

AER-004 introduces:

```text
src/test/java/dev/comfyfluffy/caustica/rt/RtRewriteCharacterizationTest.java
```

Its purpose is to protect rewrite-sensitive behavior including:

- ReSTIR current / previous ownership
- temporal reset behavior
- LOD source reuse
- retention of old LOD proxy during incomplete replacement
- Voxy / Distant Horizons provider priority
- upscaler mutual exclusion
- NRD disabled baseline behavior

AER-004 must not change rendering algorithms.

## Known ReSTIR baseline issue

ReSTIR currently shows temporal boiling / flickering.

This is a pre-refactor baseline issue.

It must not be fixed as part of AER-004 or ownership-only refactoring.

Any ReSTIR sampling or reservoir-quality fix must be handled separately.

## Required resolution

The following GATE-0 requirements were completed:

1. run the full test suite including `RtRewriteCharacterizationTest`;
2. confirm that the new characterization tests pass;
3. confirm that no new failures were introduced;
4. characterize the 9 existing shader regression failures;
5. determine whether each failure represents:
   - a stale regression test;
   - an accepted baseline failure;
   - or a genuine defect requiring separate work;
6. freeze the accepted four-failure baseline policy.

## Restrictions

Until BLOCKER-001 is resolved:

- do not begin AER-010;
- do not change ReSTIR mathematics;
- do not rewrite production shaders just to make the 4 existing tests pass;
- do not change the validated reference tag.

## Safe next action

Run the complete Gradle test suite with the AER-004 characterization test when
revalidating the baseline.

Then compare the failure set against the validated baseline.
## Resolution

BLOCKER-001 is closed.

Validated on `rewrite/aer` against:

```text
reference/validated-caustica-0.2.0
3c54fc201f93598246db62ebb1947dfb1e274e92
Final GATE-0 validation:
- git diff --check: PASS
- RtRewriteCharacterizationTest: 7/7 PASS
- full test suite: 139 tests, exact frozen 4-test baseline failure set
- new failures introduced by rewrite scaffolding: 0
- shader compilation: PASS
- Gradle build excluding already-characterized tests: PASS
- validate-fast.ps1: PASS
- validate-build.ps1: PASS
The four genuine shader regression failures are accepted only as the frozen
pre-refactor baseline. Any different failure set is a validation failure.
The known ReSTIR temporal boiling/flickering issue remains outside the architectural rewrite and must not be changed as part of ownership migration tasks.
GATE-0: PASS

## BLOCKER-002 — Deferred DH/Voxy runtime qualification

**Status:** WAIVED FOR THE CURRENT FINAL CLOSURE SCOPE

**Blocks:**

- Follow-up DH/Voxy provider qualification only; excluded from the current closure by explicit user directive

## Current situation

AER-040..046 are DONE. Their integration audit, targeted GPU/LOD/terrain/entity
tests, exact baseline comparison, V1 and V2 pass. The current build is installed
byte-for-byte in the Modrinth `prova` instance as its sole active
`caustica-0.2.4.jar`, with the previous JAR preserved outside the active mods
directory.

The current candidate completed a manually launched base smoke: integrated world
load, RT terrain activation, about 35 seconds in-world, return to menu, all-world
save and clean shutdown without logged error/device-lost/lifetime-underflow
evidence. The run did not request the Vulkan validation layer, did not include DH
or Voxy, and did not demonstrate entity/refit activity in its log.

The user reports a separate unresolved bug in the DH/Voxy runtime path. Root
cause and fix are not yet characterized. Investigation is deliberately deferred
until the end of the architectural refactoring; no speculative provider,
meshing, selector or lifetime change is authorized as part of GATE-4 recovery.
The missing LOD smoke is classified as `DEFERRED_BASELINE_ISSUE` under the
narrow canonical GATE-4 exception. It is not a PASS and remains required before
the final refactoring gate, but it no longer independently blocks GATE-4.

The current candidate's Vulkan run satisfies the repository V3 contract: Vulkan
backend selection, RT bring-up, world load, terrain and entity frame-path
execution, return to menu and clean shutdown without new Vulkan/device-lost/
lifetime errors. The optional Khronos validation layer was available but not
explicitly requested.

## Required resolution

After the main architectural refactoring, separately diagnose and resolve the
known DH/Voxy bug, then run movement/chunk-streaming smoke with a compatible
active provider before final qualification.

GATE-4 may remain PASS under its canonical `DEFERRED_BASELINE_ISSUE` exception.
Under the original roadmap scope this blocker must be resolved before an
unwaived all-provider qualification can pass.

### 2026-09-09 scope override

The user now explicitly excludes further DH/Voxy and FSR/XeSS qualification
from this closure attempt. BLOCKER-002 is waived for that scope only, not fixed
or runtime-qualified. LOD rebuild/reuse remains an unmeasured AER-093 evidence
field; it is not an additional explicit waiver or standalone FINAL criterion.
Its historical account above is retained for traceability.

## BLOCKER-003 — Final integration and qualification gaps

Status: CLOSED.

### Final resolution

REMEDIATION-04 through REMEDIATION-06 transferred world-trace, trace-frame, Frame Generation and
presentation ownership out of `RtComposite`. REMEDIATION-08 established global device quiescence
before GPU owner teardown while preserving normal exact-token and frame-tail retirement.

Final runtime evidence qualifies water/glass, LabPBR, ReSTIR, SHaRC, SVGF, HDR and clean shutdown.
The user explicitly classified NRD as `OUT OF TARGET / NOT REQUIRED` and complete end-to-end GPU
frame timing as `OUT OF SCOPE / NOT REQUIRED`; neither is PASS or a waiver. The exact waiver set
remains DH, Voxy, FSR and XeSS. All requirements remaining in the agreed FINAL target are satisfied
at candidate `98ce86e555376a671fcbc753d551e8a9d55cbc7d`.

### Historical audit state

Audit at `fd7ce60`: `RtComposite.ensureOutput`, `destroy` and FG/presentation
methods still own resource allocation/lifetime, so the strict FINAL
orchestration-only target is not demonstrated. Narrow AER-090 acceptance covers
already-migrated lifetimes only. `FramePipeline` also retains a test-used linear
execution API after production switched exclusively to `GraphExecution`.
These findings require a bounded recovery/disposition, not an incidental
ownership change inside a DOC_ONLY report.

No matched frozen-reference/final GPU average, P95/P99, VRAM or BLAS dataset is
available in the examined evidence. Historical GATE-8 A/B measures a CPU-side
envelope for two flags in the same intermediate JAR and cannot satisfy FINAL.

An agent-operated smoke was prepared but desktop inspection failed with
`Computer Use app approval timed out`. No route was executed. This is NOT RUN,
not a validation PASS. Optional paths excluded by the user are not blockers
for this attempt. See `FINAL_REPORT.md` for exact sources, hashes and recovery.

### Superseded disposition before remediation

The inactive `FramePipeline` runner was removed in `8204626` and POST image,
pipeline and exposure ownership moved out of `RtComposite` in `eda7606`.
Those bounded migrations remain valid, but the forensic audit demonstrates
that significant guide, trace, continuation, Frame Generation and presentation
resources are still created, destroyed and recreated directly by
`RtComposite`. Therefore the canonical orchestration/delegation-only and full
GPU-lifetime-centralization targets are not satisfied.

Historical demonstrated canonical failures, now resolved:

- `RtComposite` is not orchestration/delegation-only;
- GPU lifetime is not fully centralized;
- the `RtComposite` delegation-only integration target is not satisfied.

Historical qualification blocker, now removed from agreed scope by explicit user decision:

- canonical complete GPU frame-time Average/P95/P99 coverage is blocked because
  the retained timestamp interval covers only the composite command buffer;
- matched frozen-reference-vs-rewrite Vulkan validation is unavailable as
  qualification evidence, but is not a separate FINAL requirement row.

Historical runtime not-tested areas, subsequently qualified except NRD:

- water/glass, LabPBR, ReSTIR runtime, full SHaRC qualification, direct SVGF
  dispatch qualification, HDR and NRD.

Authorized waivers, exactly:

- DH;
- Voxy;
- FSR;
- XeSS.

Non-canonical known limitations:

- inherited ReSTIR boiling/flickering;
- inherited exceptional resize/unwind risk;
- known DH/Voxy provider bug.
