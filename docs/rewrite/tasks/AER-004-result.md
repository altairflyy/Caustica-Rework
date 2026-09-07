# AER-004 Result

## Summary

AER-004 characterization validation is complete against the validated Caustica 0.2.0 baseline.

## Reference

Validated reference:

```text
reference/validated-caustica-0.2.0
3c54fc201f93598246db62ebb1947dfb1e274e92
Working branch:
rewrite/aer
Characterization tests
RtRewriteCharacterizationTest executed:
7 tests
7 passed
0 failed
Validated behaviors:
- ReSTIR current / previous ping-pong ownership
- legacy temporal reset behavior
- LOD source/version reuse
- incomplete LOD replacement retains published proxy
- Voxy priority before Distant Horizons fallback
- temporal upscaler mutual exclusion
- NRD disabled baseline
Full test suite comparison
The rewrite branch reproduces exactly the validated-reference failure set.
Known failures:
RtParallaxShaderRegressionTest
- sideWallsReplaceTheMappedNormalOnBothHitPaths
- blockSpritesTileWhileEntityAtlasesStopAtTheirIsland
- crossingBudgetFromJavaStaysInsideTheShaderBounds
- columnHeightsAreTexelExactAndStayInsideTheSprite
- everyHeightSampleIsBoundedByTheCrossingBudget
- reliefDepthStillCollapsesWhenPomIsDisabled
- heightFieldIsWalkedAsAColumnGridNotAsDepthLayers
RtWaterWaveShaderRegressionTest
- continuationOriginsStayOffTheRestPlaneMesh
- animatedWaterIntersectsTheHeightFieldAlongTheViewRay
Result:
Known baseline failures: 9
New failures introduced by rewrite scaffolding: 0
Shader compilation compatibility fix
The current Slang compiler rejects an uninitialized MaterialHeader in:
shaders/world/world.rahit.slang
The following initialization is required:
MaterialHeader materialHeader = {};
This is a toolchain/compiler-safety initialization and does not intentionally change shader algorithms.
Known ReSTIR issue
ReSTIR temporal boiling / flickering remains present and is intentionally unchanged.
Validation
- Reference identity: PASS
- Shader compilation: PASS
- AER-004 characterization tests: PASS
- Baseline failure equivalence: PASS
- Additional failures: 0
- ReSTIR math changed: NO
State
DONE
The exact 9-test failure set is frozen as the accepted pre-refactor baseline.
GATE-0 may proceed once migration state and validation policy are updated.
