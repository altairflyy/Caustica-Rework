# Final rewrite audit report — 2026-09-09

## 1. Scope and reference

- **Task classification:** AER-093 (`DOC_ONLY` final architecture and benchmark evidence report).
- **Frozen reference commit:** `3c54fc201f93598246db62ebb1947dfb1e274e92` (`reference/validated-caustica-0.2.0`).
- **Rewrite report HEAD:** `eda7606e4bb9817ec43ad78a046f99494289b734` on branch `rewrite/aer`.
- **Status of this report:** Documentation report of verified evidence, observed runtime findings, authorized scope waivers, and unresolved evidence gaps. It does NOT assert that all final roadmap criteria are satisfied, nor does it declare a `FINAL: PASS`.

---

## 2. Architecture status

| Architectural boundary | Implementation & runtime status | Evidence & assessment |
| --- | --- | --- |
| **Graph execution authority** | `GraphExecution` is the sole runtime execution authority. Topological order enforced. | **VERIFIED**. `RtComposite.java:785` advances `pipelineCursor = graphExecution.begin(frameContext)`. No legacy cursor branch exists. |
| **Linear frame runner** | Obsolete `FramePipeline` runner (`execute`, `begin`, `Cursor`) retired. | **VERIFIED**. Deleted in commit `8204626`. `FramePipeline.java` is strictly an immutable callback container (37 lines). |
| **Development gates** | Obsolete `RewriteGates` class and all 11 `engine.*V2` keys removed. | **VERIFIED**. Deleted in commit `fd7ce60`. Zero `engine.*V2` or `RewriteGates` references remain in `src/main`. |
| **Synchronization authority** | Four generated barrier plans: POST, Denoiser, Upscaler, PathTrace. | **VERIFIED**. Zero manual barrier fallback paths remain in emitter classes (`DenoiserBarriers`, `UpscalerBarriers`, `PostImageBarriers`, `PathTraceBarriers`). |
| **Cross-queue synchronization** | `QueueDependencyScheduler` centralizes timeline schedules (AER-084). | **VERIFIED**. `RtGpuExecutor.java:54` instantiates and delegates timeline scheduling to `queueDependencies`. |
| **Post-processing ownership** | `PostProcessing` owns display images, auto-exposure, tonemapping pipeline. | **VERIFIED**. Extracted in commit `eda7606` (AER-090). `RtComposite` no longer owns display images or tonemapping dispatch. |
| **Legacy execution authority** | No duplicate legacy execution authority was identified in the audited runtime paths. | **VERIFIED**. AER-091 audit demonstrated single canonical authority across all 7 canonical domains. |
| **RtComposite orchestration-only** | Full reduction of `RtComposite` to pure frame coordinator. | **REQUIRES FINAL RE-AUDIT**. PostProcessing was extracted, but trace, continuation, and guide resource allocations remain in composite. |

---

## 3. Feature parity

| Feature | Implementation | Runtime qualification | Status | Evidence / note |
| --- | --- | --- | --- | --- |
| **Vulkan RT core** | `RtContext`, `RtPipeline`, `RtAccel` | Exercised in Nether smoke run | **RUNTIME VERIFIED** | Nether smoke `B-655481cb...`: ray tracing active, TLAS rebuilt, valid frame presentation. |
| **DLSS Ray Reconstruction** | `DlssRrReconstructionBackend` | Exercised in Nether smoke run | **RUNTIME VERIFIED** | Nether smoke `B-655481cb...`: NGX DLSS-RR denoises and upscales to display resolution. |
| **ReSTIR GI / DI** | `RestirSystem`, `RestirHistory` | Exercised in Nether smoke run | **RUNTIME EXERCISED / QUALITATIVE PARITY NOT DEMONSTRATED** | Nether smoke: primary/indirect traces exchange reservoir ping-pong buffers (`restirCurrent`/`restirPrevious`). Pre-existing temporal boiling / flickering remains visible under low SPP. |
| **Scene / TLAS** | `SceneAssembler`, `RtEntities` | Exercised in Nether smoke run | **RUNTIME VERIFIED** | Nether smoke: dynamic entity BLAS and static terrain instances merged via `SceneAssembler.tlasInput`. |
| **Auto-Exposure / Tonemap** | `PostProcessing`, `RtExposure` | Exercised in Nether smoke run | **IMPLEMENTED / RUNTIME PATH VERIFIED** | Nether smoke: histogram generation, resolve dispatch, tonemap dispatch executed every frame. Adaptation dynamics were not specifically measured. |
| **SDR Output Presentation** | `PostProcessing`, `RtFramePresenter` | Exercised in Nether smoke run | **RUNTIME VERIFIED** | Nether smoke: SDR presentation blit to swapchain executed cleanly. |
| **SHaRC Radiance Cache** | `SharcRadianceCache` | Not exercised in final smoke | **IMPLEMENTED / RUNTIME NOT VERIFIED** | Experimental NVIDIA Spatial Hash Radiance Cache present in code; disabled by default in smoke runs. |
| **SVGF Denoiser** | `SvgfReconstructionBackend` | Not exercised in final smoke | **IMPLEMENTED / RUNTIME NOT VERIFIED** | Historical coverage in GATE-7; final candidate smoke used DLSS-RR path exclusively. |
| **HDR Presentation** | `PostProcessing`, `RtComposite` | Not exercised in final smoke | **IMPLEMENTED / RUNTIME NOT VERIFIED** | HDR pipeline present in code; smoke runs executed exclusively under SDR. |
| **NRD Denoiser** | `NrdReconstructionBackend` | Not exercised in final smoke | **IMPLEMENTED / RUNTIME NOT VERIFIED** | NRD seam present in code; native runtime DLL not supplied. |
| **LOD (Distant Horizons)** | `RtLodTerrain`, `LodBuildSession` | Excluded from qualification | **WAIVED / OUT OF QUALIFICATION SCOPE** | Known pre-existing crash bug; excluded by user directive. |
| **LOD (Voxy Bridge)** | `RtLodTerrain`, `LodProviderSelector` | Excluded from qualification | **WAIVED / OUT OF QUALIFICATION SCOPE** | Untested in runtime smoke; excluded by user directive. |
| **AMD FSR 3.1 Upscaler** | `FsrUpscalerBackend` | Excluded from qualification | **WAIVED / OUT OF QUALIFICATION SCOPE** | Optional native SDK not supplied; excluded by user directive. |
| **Intel XeSS Upscaler** | `XessUpscalerBackend` | Excluded from qualification | **WAIVED / OUT OF QUALIFICATION SCOPE** | Optional native SDK not supplied; excluded by user directive. |

---

## 4. Test status

- **Execution command:** `.\scripts\agent\validate-build.ps1`
- **Total executed:** 248
- **Passed:** 244
- **Canonical expected failures:** 4 (exact match with `$ExpectedFailures` baseline):
  - `dev.comfyfluffy.caustica.rt.RtParallaxShaderRegressionTest::sideWallsReplaceTheMappedNormalOnBothHitPaths`
  - `dev.comfyfluffy.caustica.rt.RtParallaxShaderRegressionTest::blockSpritesTileWhileEntityAtlasesStopAtTheirIsland`
  - `dev.comfyfluffy.caustica.rt.RtWaterWaveShaderRegressionTest::continuationOriginsStayOffTheRestPlaneMesh`
  - `dev.comfyfluffy.caustica.rt.RtWaterWaveShaderRegressionTest::animatedWaterIntersectsTheHeightFieldAlongTheViewRay`
- **Unexpected failures:** 0
- **Characterization tests:** 7/7 PASS (`dev.comfyfluffy.caustica.rt.RtRewriteCharacterizationTest`)
- **V2 Build:** PASS (`caustica-0.2.0.jar` built, NGX shim present at 83,968 bytes).
- **Cardinality note:** Current suite is not cardinality-identical to the frozen-reference suite (contains new structural and ownership qualification tests added during the rewrite).

---

## 5. Performance evidence

### Gate-8 Internal A/B Toggle Benchmark (Historical Evidence)

The following metrics were captured during Gate-8 qualification using the same development candidate JAR (`caustica-0.2.8-gate8-candidate.jar`, SHA-256: `D6B31873072FDEBA92F71621E087BEEC0A6183EA6346E941B350C3FEAEC7FD9A`) over 600 active sampled frames in a stationary Nether workload:

| Metric (CPU envelope) | Mode A (`generated: false`) | Mode B (`generated: true`) | Delta | Source artifact |
| --- | ---: | ---: | ---: | --- |
| **Average CPU envelope** | 7.3314 ms | 8.3637 ms | +14.08% | `GATE-8-performance/A-.../metrics.json` & `B-.../metrics.json` |
| **P95 CPU envelope** | 9.214 ms | 9.699 ms | +5.26% | `GATE-8-performance/A-.../metrics.json` & `B-.../metrics.json` |
| **P99 CPU envelope** | 10.472 ms | 11.207 ms | +7.02% | `GATE-8-performance/A-.../metrics.json` & `B-.../metrics.json` |

> [!IMPORTANT]
> **Performance provenance boundary:**
> These values compare two Gate-8 execution modes of the same development JAR. They DO NOT constitute frozen-reference-0.2.0-vs-final-rewrite performance evidence.

### Canonical Reference vs Rewrite Performance Status

- **CPU final comparison:** **NOT VERIFIED** (no matched frozen-reference 0.2.0 benchmark dataset available).
- **P95 final comparison:** **NOT VERIFIED** (no matched frozen-reference 0.2.0 benchmark dataset available).
- **P99 final comparison:** **NOT VERIFIED** (no matched frozen-reference 0.2.0 benchmark dataset available).

---

## 6. GPU, VRAM, and BLAS evidence

- **GPU performance:** **NOT VERIFIED**. Frame time metrics in `frame.csv` record host CPU dispatch envelopes via `RtFrameStats`; no hardware GPU timestamp queries (`vkCmdWriteTimestamp`) were recorded. Vulkan validation log cleanliness does not constitute GPU performance proof.
- **VRAM steady-state:** **NOT VERIFIED**. No comparative VMA allocation or dedicated GPU memory dump exists between frozen reference 0.2.0 and the final candidate.
- **BLAS comparative counts:** **NOT VERIFIED**. Although `gpuAsLiveCount` is tracked in rewrite diagnostics, no baseline dataset exists for reference 0.2.0 under identical workload.

---

## 7. LOD rebuild / reuse

- **LOD rebuild / reuse metrics:** **NOT VERIFIED**.
- **Evidence status:** No statistical dataset of chunk rebuild frequency or proxy mesh reuse exists.
- **Waiver status:** NONE authorized for generic LOD metrics.

---

## 8. Device compatibility

- **Host platform qualified at runtime:** Windows 11 x86_64, NVIDIA GeForce RTX series GPU, Vulkan 1.4, DLSS Ray Reconstruction.
- **Architecturally supported but runtime unverified on final candidate:** Linux (Mixins and platform hooks present), AMD Radeon (FSR path), Intel Arc (XeSS path).
- **Status:** **PARTIALLY VERIFIED** (strictly limited to demonstrated host configuration).

---

## 9. Known baseline issues

Demonstrated pre-existing issues inherited from reference baseline:
1. **Canonical shader failures:** 4 expected test failures in parallax and water wave compute shaders.
2. **ReSTIR temporal boiling / flickering:** Pre-existing temporal accumulation noise characteristic of the original 0.2.0 implementation under low SPP.

---

## 10. Observed validation findings

Findings observed during runtime qualification smoke runs:
1. **DH vertex-input format VUID:** Observed when Distant Horizons classes are touched during startup.
2. **NGX DLSSD WAW hazards:** Driver-internal Write-After-Write synchronization hazard reports logged inside NVIDIA NGX library dispatch.
3. **Shutdown child-object leak report:** Vulkan debug report logging child allocations during context tear-down.

> [!NOTE]
> These findings were observed during runtime qualification; they are not proven rewrite-introduced unless matched comparative evidence from frozen reference 0.2.0 demonstrates their absence in the baseline.

---

## 11. Known rewrite limitations and unproven areas

1. **Exceptional resize lifecycle:**
   - Nominal resize lifecycle: **VERIFIED** (resources reallocated correctly on window resize).
   - Exceptional / unwind lifecycle: **NOT PROVEN** (`releaseImagesForResize` destroys display images without nulling references before `createImages()`; an intervening exception could leave stale handles).
   - Potential double-destroy risk: **KNOWN INHERITED BASELINE RISK**.
   - Regression introduced by AER-090: **NOT DEMONSTRATED**.
2. **Shader source diff:** `src/main/resources/assets/caustica/shaders/rt/world.rahit.slang` contains a single fix (zero-initialization of `MaterialHeader materialHeader`). The full rewrite is not zero-diff against reference shaders.
3. **Desktop automation boundary:** Automated launcher interaction timed out during intermediate testing, requiring user-completed or agent-interactive smoke execution.

---

## 12. Known improvements

Demonstrable architectural and structural improvements introduced by the rewrite:
1. **Single topological execution authority:** `GraphExecution` replaces dual execution modes and eliminates linear runner branching.
2. **Deterministic barrier generation:** Elimination of all manual conservative fallback barriers across POST, Denoiser, Upscaler, and PathTrace.
3. **Cross-queue synchronization:** Centralized `QueueDependencyScheduler` enforcing Vulkan timeline semaphore order between async compute and graphics.
4. **Clean codebase boundaries:** Full deletion of development-only `RewriteGates` and retirement of inactive linear runner from `FramePipeline`.
5. **Decoupled ownership:** Extraction of `PostProcessing` from `RtComposite`, isolating display, tonemapping, and auto-exposure lifecycles.

---

## 13. Waivers

The following four features are formally classified as waived from the current qualification scope by explicit user directive:

1. **Distant Horizons (DH):** `WAIVED / OUT OF QUALIFICATION SCOPE`
2. **Voxy:** `WAIVED / OUT OF QUALIFICATION SCOPE`
3. **AMD FSR 3.1:** `WAIVED / OUT OF QUALIFICATION SCOPE`
4. **Intel XeSS:** `WAIVED / OUT OF QUALIFICATION SCOPE`

> [!IMPORTANT]
> **Waiver boundary definition:**
> These requirements remain canonical requirements in `ROADMAP.md`. The waiver adjusts the user-qualified closure scope for this audit attempt; it does NOT convert the requirements to `PASS`, does NOT satisfy them, and does NOT alter `ROADMAP.md`.
> No other requirement or metric (including LOD rebuild/reuse, GPU performance, or VRAM) is waived.

---

## 14. Evidence gaps

The following requirements lack direct comparative evidence between frozen reference 0.2.0 (`3c54fc20`) and rewrite HEAD (`eda7606`):
1. Matched CPU frame time comparison against reference 0.2.0.
2. Hardware GPU timestamp execution metrics.
3. Matched P95 and P99 latency comparisons against reference 0.2.0.
4. Steady-state VRAM consumption comparison.
5. Matched BLAS count comparison under identical world scenes.
6. Statistical LOD rebuild/reuse efficiency data.
7. Runtime verification of HDR presentation mode and SVGF standalone denoiser on the final candidate.

---

## 15. AER-093 conclusion

- **AER-093 report status:** **COMPLETE AS DOCUMENTATION**.
- **Assessment:** This report comprehensively and transparently records all verified architectural accomplishments, observed runtime findings, authorized scope waivers, and remaining evidence gaps.
- **FINAL GATE disposition:** **PENDING / NOT QUALIFIED**. Documenting evidence gaps completes the `DOC_ONLY` reporting requirement of AER-093, but does NOT constitute a `FINAL: PASS` of the migration roadmap. Final gate disposition remains subject to subsequent evaluation.
