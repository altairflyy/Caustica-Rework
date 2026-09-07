# Caustica Rewrite Baseline

## Reference identity

- Upstream repository: `xysgottaken2/Caustica`
- Reference version: `0.2.0`
- Minecraft: `26.2`
- Fabric Loader: `0.19.3`
- Fabric API: `0.153.0+26.2`
- Loom: `1.17-SNAPSHOT`
- Reference commit: `3c54fc201f93598246db62ebb1947dfb1e274e92`
- Immutable validated tag: `reference/validated-caustica-0.2.0`
- Working branch: `rewrite/aer`

The rewrite starts directly from the real upstream Git commit above.

The previous temporary imported snapshot is no longer used as the rewrite reference.

## Validated DLSS-RR baseline

The source tree already contains the `ngxshim_evaluate_dlssd` export in:

`native/ngx_shim/ngx_shim.cpp`

The previously bundled/precompiled `ngxshim.dll` did not expose that export.

The NGX shim was rebuilt from the validated reference source and then packaged into the Caustica JAR.

Validated NGX shim:

- `ngxshim.dll` size: `83968` bytes
- `ngxshim_evaluate_dlssd`: present
- DLSS / DLSS-RR NVIDIA libraries bundled in the JAR
- DLSS-RR runtime initialization: PASS
- external `[ngx] path` override: not required
- Minecraft launch with rebuilt JAR: PASS

Validated Gradle build parameters:

```text
-PngxShimConfig=release
-PngxVendorConfig=rel
```

Generated DLLs and other build outputs are not part of the Git reference.

The Git reference remains:

```text
reference/validated-caustica-0.2.0
3c54fc201f93598246db62ebb1947dfb1e274e92
```

## Runtime validation

Runtime validation was performed on the Windows gaming host.

Validated:

- Minecraft starts successfully
- Vulkan ray tracing starts successfully
- DLSS-RR initializes successfully
- rebuilt NGX shim is loaded from the packaged mod
- no external NGX override is required

The build emitted warnings because optional FSR, NRD and XeSS native artifacts were not available locally.

These warnings do not invalidate the NGX / DLSS-RR baseline.

## Known baseline issue: ReSTIR

ReSTIR currently exhibits temporal boiling / flickering.

This behavior existed before the architectural rewrite.

It is therefore considered a known baseline issue.

During ownership-only and architectural refactoring tasks:

- do not change ReSTIR sampling mathematics;
- do not treat the boiling as a regression introduced by the rewrite;
- do not mix a ReSTIR quality fix with architectural refactoring.

A ReSTIR quality fix must be handled separately.

## Test baseline

The validated reference test suite was executed before architectural refactoring.

Result:

```text
123 tests
114 passed
9 failed
```

The existing failures are:

### RtParallaxShaderRegressionTest

1. `sideWallsReplaceTheMappedNormalOnBothHitPaths`
2. `blockSpritesTileWhileEntityAtlasesStopAtTheirIsland`
3. `crossingBudgetFromJavaStaysInsideTheShaderBounds`
4. `columnHeightsAreTexelExactAndStayInsideTheSprite`
5. `everyHeightSampleIsBoundedByTheCrossingBudget`
6. `reliefDepthStillCollapsesWhenPomIsDisabled`
7. `heightFieldIsWalkedAsAColumnGridNotAsDepthLayers`

### RtWaterWaveShaderRegressionTest

1. `continuationOriginsStayOffTheRestPlaneMesh`
2. `animatedWaterIntersectsTheHeightFieldAlongTheViewRay`

These nine failures were observed before architectural refactoring.

They must be characterized before `GATE-0` is declared PASS.

Production shaders must not be changed merely to make these tests green until it is determined whether:

- the regression tests are stale;
- the expected behavior has changed;
- or the validated reference actually contains a defect.

## AER-000 result

- Reference identifiable: PASS
- Real upstream Git identity preserved: PASS
- Immutable validated reference tag created: PASS
- Working tree clean at reference creation: PASS
- DLSS-RR runtime validation: PASS
- Known baseline issues documented: PASS

## Current rewrite state

Current working branch:

```text
rewrite/aer
```

Validated reference:

```text
reference/validated-caustica-0.2.0
```

Reference commit:

```text
3c54fc201f93598246db62ebb1947dfb1e274e92
```

## GATE-0 status

`GATE-0`: BLOCKED

Reason:

AER-004 characterization validation still needs to be completed.

The nine existing shader regression failures must also be characterized and either:

- formally accepted as the frozen baseline failure set;
- corrected at the test level if the tests are stale;
- or handled separately if they reveal genuine defects.

No architectural refactoring task such as AER-010 should begin until GATE-0 is resolved.