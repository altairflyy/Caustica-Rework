# Rewrite Blockers

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

**Status:** DEFERRED UNTIL FINAL

**Blocks:**

- FINAL

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
This blocker must be resolved before `FINAL` can pass.
