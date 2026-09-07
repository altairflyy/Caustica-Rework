# Rewrite Blockers

## BLOCKER-001 — GATE-0 baseline characterization

**Status:** OPEN

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

Existing failures:

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

These failures existed before architectural refactoring.

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

Before `GATE-0` can become `PASS`:

1. run the full test suite including `RtRewriteCharacterizationTest`;
2. confirm that the new characterization tests pass;
3. confirm that no new failures were introduced;
4. characterize the 9 existing shader regression failures;
5. determine whether each failure represents:
   - a stale regression test;
   - an accepted baseline failure;
   - or a genuine defect requiring separate work;
6. freeze the accepted baseline failure policy.

## Restrictions

Until BLOCKER-001 is resolved:

- do not begin AER-010;
- do not change ReSTIR mathematics;
- do not rewrite production shaders just to make the 9 existing tests pass;
- do not change the validated reference tag.

## Safe next action

Run the complete Gradle test suite with the imported AER-004 characterization test.

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
- full test suite: 130 tests, exact frozen 9-test baseline failure set
- new failures introduced by rewrite scaffolding: 0
- shader compilation: PASS
- Gradle build excluding already-characterized tests: PASS
- validate-fast.ps1: PASS
- validate-build.ps1: PASS
The 9 shader regression failures are accepted only as the frozen pre-refactor baseline. Any different failure set is a validation failure.
The known ReSTIR temporal boiling/flickering issue remains outside the architectural rewrite and must not be changed as part of ownership migration tasks.
GATE-0: PASS
