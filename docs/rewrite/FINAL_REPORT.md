# Final rewrite audit report — 2026-09-09

## 1. Scope and reference

- **Task classification:** AER-093 (`DOC_ONLY` final architecture and benchmark evidence report).
- **Frozen reference commit:** `3c54fc201f93598246db62ebb1947dfb1e274e92` (`reference/validated-caustica-0.2.0`).
- **Production code audited at:** `7b58411511a29c32e94bfdd73eff2b4464d5bd0b` on branch `rewrite/aer`; the final closure changes only documentation and the stale `FrameGraph` Javadoc.
- **Status of this report:** Final repository-wide consistency report. The previous `FINAL: PASS` disposition is withdrawn; canonical qualification remains pending remediation. DH/Voxy and FSR/XeSS remain the only authorized waivers, not newly qualified features.

---

## 2. Architecture status

| Architectural boundary | Implementation & runtime status | Evidence & assessment |
| --- | --- | --- |
| **Graph execution authority** | `GraphExecution` is the sole runtime execution authority. Topological order enforced. | **VERIFIED**. `RtComposite.java:785` advances `pipelineCursor = graphExecution.begin(frameContext)`. No legacy cursor branch exists. |
| **Linear frame runner** | Obsolete `FramePipeline` runner (`execute`, `begin`, `Cursor`) retired. | **VERIFIED**. Deleted in commit `8204626`. `FramePipeline.java` is strictly an immutable callback container (37 lines). |
| **Development gates** | Obsolete `RewriteGates` class and all 11 `engine.*V2` keys removed. | **VERIFIED**. Deleted in commit `fd7ce60`. Zero `engine.*V2` or `RewriteGates` references remain in `src/main`. |
| **Synchronization authority** | Four generated barrier plans: POST, Denoiser, Upscaler, PathTrace. | **VERIFIED**. Zero manual barrier fallback paths remain in emitter classes (`DenoiserBarriers`, `UpscalerBarriers`, `PostImageBarriers`, `PathTraceBarriers`). |
| **Cross-queue synchronization** | `QueueDependencyScheduler` centralizes timeline schedules (AER-084). | **VERIFIED**. `RtGpuExecutor.java:54` instantiates and delegates timeline scheduling to `queueDependencies`. |
| **Post-processing ownership** | `PostProcessing` owns the resources extracted by AER-090. | **VERIFIED WITH BOUNDARY**. The AER-090 extraction remains valid; separate presentation resources still remain in `RtComposite`. |
| **Legacy execution authority** | No duplicate legacy execution authority was identified in the audited runtime paths. | **VERIFIED**. AER-091 audit demonstrated single canonical authority across all 7 canonical domains. |
| **RtComposite target** | Orchestration/delegation only; not the universal GPU-resource owner. | **FAIL**. `RtComposite` still directly creates, destroys and recreates significant guide, trace, continuation, Frame Generation and presentation resources. The FINAL target does not permit retaining ownership merely because a prior AER did not migrate it. |
| **Required rewrite components** | Every architecture component introduced under `src/main` has a production consumer. | **VERIFIED**. Repository-wide class/reference audit found no required main-source scaffold referenced only by tests. |
| **AS/temporal/scene/pipeline/render-graph owners** | One authority per migrated non-GPU domain. | **VERIFIED FOR MIGRATED DOMAINS**. `AccelerationStructureManager`, `TemporalState`, `RestirSystem`, `SharcRadianceCache`, `SvgfReconstructionBackend`, `UpscalerRuntime`, `SceneAssembler` and `GraphExecution` retain their supported domain-specific roles. This does not establish centralized ownership of all GPU resources. |

---

## 3. Feature parity

| Feature | Implementation | Runtime qualification | Status | Evidence / note |
| --- | --- | --- | --- | --- |
| **Overworld** | `RtComposite`, terrain runtime | Current-candidate playthrough | **TESTED** | GATE-7 playthrough and RT activation. |
| **Nether** | `RtComposite`, RT scene | Matched benchmark and smoke | **TESTED** | Fixed Nether scenario with active RT/DLSS-RR path. |
| **End** | `RtComposite`, world transitions | Current-candidate playthrough | **TESTED** | GATE-7 transition and visual confirmation. |
| **entities** | `RtEntities`, `SceneAssembler` | Nether smoke/benchmark | **TESTED** | Entity capture and BLAS/TLAS statistics recorded. |
| **particles/weather** | `RtWeatherCapture`, particle path | Rain/snow playthrough | **TESTED** | Rain and snow observations plus particle counters. |
| **water/glass** | Water/parallax shader paths | No runtime scenario retained | **NOT TESTED** | Existing characterization is not runtime qualification. |
| **LabPBR** | Material registry/atlas path | No dedicated runtime scenario retained | **NOT TESTED** | Static material support does not prove feature parity. |
| **LOD DH** | `RtLodTerrain`, DH adapter | Provider qualification excluded | **WAIVED / OUT OF QUALIFICATION SCOPE** | Known provider bug; no runtime correctness claim. |
| **LOD Voxy bridge** | `RtLodTerrain`, Voxy adapter | Provider qualification excluded | **WAIVED / OUT OF QUALIFICATION SCOPE** | No runtime correctness claim. |
| **ReSTIR** | `RestirSystem`, `RestirHistory` | Structural/math tests; no dedicated runtime qualification | **NOT TESTED FOR CANONICAL RUNTIME QUALIFICATION** | Structural/math tests pass; known boiling remains unchanged. |
| **SHaRC** | `SharcRadianceCache` | Matched benchmark initialization observed | **NOT TESTED FOR FULL CANONICAL QUALIFICATION** | Allocation, enable and reset are logged; no dedicated behavior qualification. |
| **SVGF** | `SvgfReconstructionBackend` | AER-083 same-JAR barrier A/B | **NOT TESTED FOR DIRECTLY PROVEN DISPATCH QUALIFICATION** | SVGF selection marker observed; direct dispatch evidence is absent. |
| **DLSS-RR** | `DlssRrReconstructionBackend` | Nether smoke and AER-083 A/B | **TESTED** | DLSS-RR feature creation and runtime path observed. |
| **HDR** | `PostProcessing`, HDR presentation | No dedicated HDR run | **NOT TESTED** | Matched benchmark used SDR; no waiver is applied. |
| **SDR** | `PostProcessing`, `RtFramePresenter` | Nether smoke/benchmark | **TESTED** | SDR presentation path exercised. |
| **NRD** | `NrdReconstructionBackend` | Native backend unavailable | **NOT TESTED** | No native runtime qualification. |
| **FSR** | `FsrUpscalerBackend` | Native backend unavailable | **WAIVED / OUT OF QUALIFICATION SCOPE** | Authorized waiver. |
| **XeSS** | `XessUpscalerBackend` | Native backend unavailable | **WAIVED / OUT OF QUALIFICATION SCOPE** | Authorized waiver. |

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
- **Gate-8 same-candidate A/B validation:** SUPPORTING EVIDENCE only.
- **Matched frozen-reference-vs-rewrite validation:** NOT AVAILABLE.
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

### Canonical reference vs rewrite matched benchmark

Provenance: **FROZEN REFERENCE 0.2.0 + DOCUMENTED COMPILER-COMPATIBILITY PATCH + SYMMETRIC TEMPORARY PROFILING INSTRUMENTATION**.

The compiler compatibility patch was:

```text
MaterialHeader materialHeader;
->
MaterialHeader materialHeader = {};
```

The temporary profiling instrumentation was not present in production HEAD.
Both variants used Minecraft 26.2/Fabric 0.19.3, the same isolated copy of the
same Nether world and position, 2560x1440, render distance 12, SDR, DLSS-RR,
no frame generation and no DH/Voxy/FSR/XeSS/NRD. The reference source required
only the same `MaterialHeader` zero-initialization compiler-compatibility fix
already present in the rewrite. Profiling-only counters and timestamp queries
were applied symmetrically and were not committed.

| CPU dispatch envelope | Reference | Rewrite | Delta |
| --- | ---: | ---: | ---: |
| Average | 8.8065 ms | 8.7924 ms | **-0.16%** |
| P95 | 10.046 ms | 10.019 ms | **-0.27%** |
| P99 | 11.415 ms | 11.166 ms | **-2.18%** |

The CPU/VRAM window contains 1008 active reference frames over 63.103 s and
1003 active rewrite frames over 63.032 s. Average GPU utilization was 99.83%
and 99.80%, respectively.

---

## 6. GPU, VRAM, and BLAS evidence

The metric is **Composite command-buffer GPU duration**, measured with symmetric
`vkCmdWriteTimestamp` TOP/BOTTOM queries around the composite command buffer.
Each CSV contains 1088 samples; the last 600 steady-state samples were compared.

| Hardware GPU duration | Reference | Rewrite | Delta | Threshold |
| --- | ---: | ---: | ---: | ---: |
| Average | 62.4092 ms | 61.8635 ms | **-0.87%** | +5% |
| P95 | 64.6060 ms | 63.9419 ms | **-1.03%** | +7% |
| P99 | 64.9217 ms | 64.3197 ms | **-0.93%** | +10% |

Timestamp CSV SHA-256 values are
`D6375E416A9AA692E28B98E4226149FAC5597401BBDA96F905B9AF9E98DF6C74`
(reference) and
`D48F2D57A76FDB20765D72875A64893BA74F751160F579E8A3875618EC8B172C`
(rewrite). The temporary probe was removed after capture and is absent from the
production worktree.

| Memory / AS metric | Reference | Rewrite | Delta |
| --- | ---: | ---: | ---: |
| Average global VRAM | 3971.47 MiB | 4053.28 MiB | **+2.06%** |
| P95 global VRAM | 4130 MiB | 4087 MiB | **-1.04%** |
| Median live AS, 300 steady frames | 4682 | 4703 | **+0.45%** |
| P95 live AS | 4686 | 4711 | **+0.53%** |
| Median live BLAS bytes | 246,280,192 | 246,449,536 | **+0.07%** |
| P95 live BLAS bytes | 246,504,960 | 246,873,344 | **+0.15%** |

Included within the timestamp interval: ray/path tracing, ReSTIR work recorded
in the composite, denoiser, upscaler and composite post work.

Not demonstrated within the interval: UI/HUD, presentation/blit, other command
buffers, separate async/build submissions, and separate SDK submissions. Frame
Generation uses separate command buffers and was inactive in this benchmark.

**Composite GPU duration comparison: VERIFIED.**
**Canonical complete GPU frame-time Average/P95/P99: BLOCKED.**

VRAM remains inside the +10% limit and the AS/BLAS population is
baseline-equivalent for the matched scene.

---

## 7. LOD rebuild / reuse

- **AER-093 evidence field:** **NOT MEASURED**.
- **Explicit FINAL criterion:** **NO**.
- **Explicit waiver:** **NO**.
- This is not an autonomous FINAL performance blocker. It must not be represented as waived.
- Static lifecycle, retention, coverage and publication tests remain separate from runtime rebuild/reuse measurement.

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
2. **Shader source diff:** `shaders/world/world.rahit.slang` contains a single compiler-compatibility fix (zero-initialization of `MaterialHeader materialHeader`). No shader math/layout change was introduced by final recovery.
3. **Desktop automation boundary:** Native desktop automation was unavailable. Final matched measurements instead used isolated Loom clients with Quick Play and copies of the user's world; the original world was never modified.

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
> LOD rebuild/reuse is not part of the four authorized waiver entries. GPU performance, CPU latency, VRAM and BLAS evidence are not waived.

---

## 14. Remaining qualification boundaries

1. DH and Voxy runtime correctness remain explicitly waived and unresolved.
2. FSR and XeSS runtime paths remain explicitly waived and unresolved because their native runtimes were not supplied.
3. HDR remains NOT TESTED; the matched benchmark is SDR and this is not a waiver.
4. NRD remains experimental and unavailable without its native runtime.
5. The exceptional resize/unwind risk remains inherited and unproven, as recorded above.
6. Complete canonical GPU frame-time coverage remains blocked because the timestamp interval does not cover presentation/UI and other command buffers.

---

## 15. AER-093 conclusion

- **AER-093 report status:** **DONE**.
- **Assessment:** AER-093 documentation is complete. The audit demonstrates remaining canonical architecture failures and incomplete qualification.
- **Demonstrated failures:** `RtComposite` is not orchestration/delegation-only; GPU lifetime is not fully centralized; the integration delegation target is not satisfied.
- **Canonical blocked evidence:** complete GPU frame-time Average/P95/P99 coverage.
- **FINAL GATE disposition:** **FAIL**.
