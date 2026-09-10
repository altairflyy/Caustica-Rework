# Final rewrite audit report — 2026-09-09

## Candidate and agreed scope

- **Production candidate:** `98ce86e555376a671fcbc753d551e8a9d55cbc7d` on `rewrite/aer`.
- **Frozen reference:** `3c54fc201f93598246db62ebb1947dfb1e274e92` (`reference/validated-caustica-0.2.0`).
- **Closure change:** documentation/tracker only.
- **OUT OF TARGET:** NRD, by explicit user decision. This is neither PASS nor waiver.
- **OUT OF SCOPE:** complete end-to-end GPU frame-time Average/P95/P99, by explicit user decision. This is neither PASS nor waiver.
- **Authorized waivers, exactly:** DH, Voxy, FSR and XeSS.

Historical note: an earlier premature FINAL PASS was withdrawn while `RtComposite` still owned significant GPU resources, GPU lifetime had not converged and runtime qualification was incomplete. REMEDIATION-04 through REMEDIATION-08 subsequently resolved those demonstrated architecture and lifetime failures. The two explicit exclusions above do not rewrite that history.

## Architecture and integration

| Canonical requirement | Status | Current evidence |
| --- | --- | --- |
| Vulkan remains the backend | **PASS** | Production rendering, resources and submission remain Vulkan-only. |
| Iris is not a dependency | **PASS** | No Iris core/build dependency exists. |
| DH/Voxy are adapters | **PASS (architecture)** | Compatibility adapters feed neutral LOD provider boundaries; runtime qualification is waived. |
| `RtComposite` is no longer the universal owner | **PASS** | World-trace, trace-frame, FG and presentation resources have dedicated owners. |
| GPU lifetime centralized | **PASS** | Exact graphics-token retirement, frame-tail retirement and explicit shutdown quiescence cover their submission domains. |
| AS lifetime centralized | **PASS** | `AccelerationStructureManager` and `DeferredDeletionQueue` are the AS/lifetime boundary. |
| Temporal reset centralized | **PASS** | `TemporalState` is the reset and frame-snapshot authority. |
| Explicit pipeline | **PASS** | Explicit frame passes and `FramePipeline` are runtime-wired. |
| Render Graph derives from verified pipeline | **PASS** | `FrameGraph` derives declarations and `GraphExecution` executes validated topological order. |
| No required dead rewrite component | **PASS** | Every required rewrite component has a production consumer. |
| No duplicate migrated ownership | **PASS** | No parallel legacy authority remains for migrated domains. |
| Mandatory APIs present | **PASS** | Required frame, temporal, LOD, GPU/AS, backend, scene, pipeline and graph boundaries are present. |
| Development gates removed | **PASS** | No `RewriteGates` or `engine.*V2` production path remains. |
| Legacy execution unreachable | **PASS** | `GraphExecution` is the sole frame execution authority. |
| `RtComposite` orchestration/delegation only | **PASS** | It retains owner references, frame/camera assembly, routing, fallback and high-level sequencing; significant direct GPU ownership is none. |
| One semantic authority per migrated domain | **PASS** | Owner/dependency audit found no duplicate authority or reverse owner-to-`RtComposite` cycle. |
| FINAL report matches committed and validated code | **PASS** | This report targets candidate `98ce86e` and retains exact evidence boundaries. |

Current owners include `WorldTraceResources`, `TraceFrameResources`, `FrameGenerationResources`, `PostProcessing`, `AccelerationStructureManager`, `TemporalState`, `SceneAssembler` and `GraphExecution`. Dependency cycles and significant directly owned GPU resources in `RtComposite`: **NONE**.

## Functional qualification

| Feature | Final status | Evidence boundary |
| --- | --- | --- |
| Overworld | **PASS** | Qualified playthrough and RT activation. |
| Nether | **PASS** | Matched benchmark and runtime smoke. |
| End | **PASS** | World-transition playthrough. |
| entities | **PASS** | Runtime capture and AS/TLAS activity. |
| particles/weather | **PASS** | Rain and snow runtime observations. |
| water/glass | **PASS** | User-accepted final runtime qualification. |
| LabPBR | **PASS** | Authored channels observed: `spec=1906`, `normal=1899`, `labPbrEmission=19636`. |
| LOD DH | **WAIVED** | Explicit waiver; no runtime correctness claim. |
| LOD Voxy bridge | **WAIVED** | Explicit waiver; no runtime correctness claim. |
| ReSTIR | **PASS** | `lightCount=10596`, non-zero light buffer, `restirMode=1`, valid previous/current reservoir addresses and `restirPathOn=true`. |
| SHaRC | **PASS** | User-accepted final runtime qualification. |
| SVGF | **PASS** | User-accepted final runtime qualification. |
| DLSS-RR | **PASS** | Runtime feature creation and active path observed. |
| FSR | **WAIVED** | Explicit waiver; no runtime correctness claim. |
| XeSS | **WAIVED** | Explicit waiver; no runtime correctness claim. |
| HDR | **PASS** | User-accepted final runtime qualification. |
| SDR | **PASS** | Runtime presentation and matched benchmark. |
| clean shutdown | **PASS** | Runtime acceptance plus REMEDIATION-08 global quiescence before owner teardown. |
| NRD | **OUT OF TARGET / NOT REQUIRED** | Explicit user decision; not claimed qualified. |

Remaining required runtime NOT TESTED: **NONE**.

## Validation candidate

- `validate-build.ps1`: **PASS**.
- Total: **266**; passed: **266**.
- Expected failures: **0**; unexpected failures: **0**.
- `RtRewriteCharacterizationTest`: **7/7 PASS**.
- V2 Gradle build: **PASS**; NGX shim present at 83,968 bytes.

Historical: four canonical failures were retained during rewrite qualification. Current: they are
resolved as CRLF-sensitive test-harness false negatives; production shader behavior is unchanged and
the expected failure count is zero.

## Performance evidence

Provenance: **FROZEN REFERENCE 0.2.0 + DOCUMENTED COMPILER-COMPATIBILITY PATCH + SYMMETRIC TEMPORARY PROFILING INSTRUMENTATION**. The compatibility patch was `MaterialHeader materialHeader;` → `MaterialHeader materialHeader = {};`. Temporary instrumentation is absent from production HEAD.

Both variants used the same Minecraft/Fabric version, isolated copy of the same Nether world and position, 2560x1440, render distance 12, SDR and DLSS-RR, with FG and optional providers disabled.

| CPU dispatch envelope | Reference | Rewrite | Delta | Status |
| --- | ---: | ---: | ---: | --- |
| Average | 8.8065 ms | 8.7924 ms | -0.16% | **PASS** |
| P95 | 10.046 ms | 10.019 ms | -0.27% | **PASS** |
| P99 | 11.415 ms | 11.166 ms | -2.18% | **PASS** |

The symmetric timestamp CSVs contain 1088 samples each; the last 600 steady-state samples were compared.

| Composite command-buffer GPU duration | Reference | Rewrite | Delta | Threshold | Status |
| --- | ---: | ---: | ---: | ---: | --- |
| Average | 62.4092 ms | 61.8635 ms | -0.87% | +5% | **PASS** |
| P95 | 64.6060 ms | 63.9419 ms | -1.03% | +7% | **PASS** |
| P99 | 64.9217 ms | 64.3197 ms | -0.93% | +10% | **PASS** |

CSV SHA-256: reference `D6375E416A9AA692E28B98E4226149FAC5597401BBDA96F905B9AF9E98DF6C74`; rewrite `D48F2D57A76FDB20765D72875A64893BA74F751160F579E8A3875618EC8B172C`.

The interval includes composite ray/path tracing, ReSTIR, denoiser, upscaler and composite post. It does not demonstrate UI/HUD, presentation/blit, other command buffers, separate async/build/SDK submissions or active FG.

**Complete end-to-end GPU frame-time Average/P95/P99: OUT OF SCOPE / NOT REQUIRED by explicit user decision.** It is not claimed PASS and is not evidence for the passing metrics above.

| Memory / AS metric | Reference | Rewrite | Delta | Status |
| --- | ---: | ---: | ---: | --- |
| Average global VRAM | 3971.47 MiB | 4053.28 MiB | +2.06% | **PASS** |
| P95 global VRAM | 4130 MiB | 4087 MiB | -1.04% | **PASS** |
| Median live AS | 4682 | 4703 | +0.45% | supporting evidence |
| P95 live AS | 4686 | 4711 | +0.53% | supporting evidence |
| Median live BLAS bytes | 246,280,192 | 246,449,536 | +0.07% | supporting evidence |
| P95 live BLAS bytes | 246,504,960 | 246,873,344 | +0.15% | supporting evidence |

VRAM is a same-methodology global-GPU `nvidia-smi` measurement and may include background-process contamination. Its +2.06% delta remains below the canonical +10% threshold. Performance for all metrics remaining in scope: **PASS**.

## Safety and known limitations

- No new hot-path `waitIdle`: **PASS**.
- No stale cross-dimension geometry, reproducible GPU UAF/device loss, persistent LOD hole or simultaneous coarse/fine publication in qualified scope: **PASS**.
- Shutdown quiescence: **PASS**. REMEDIATION-08 stops/joins the executor, performs one global device wait and flushes deferred destruction before owner teardown; infrastructure is destroyed afterwards without another wait.
- ReSTIR boiling/flickering remains an inherited baseline issue.
- The DH/Voxy provider bug remains unresolved under the explicit waiver.
- NRD remains experimental and outside the target.
- Exceptional resize/unwind behavior remains an inherited, non-regression limitation; nominal resize is qualified.
- Gate-8 same-candidate A/B remains supporting evidence. Matched reference/rewrite validation-suite execution is unavailable and is not a distinct FINAL requirement.
- LOD rebuild/reuse is an unmeasured AER-093 report field, not an explicit FINAL criterion or waiver.

## Final disposition

- Architecture, GPU lifetime, safety, required runtime qualification, in-scope performance and integration completeness: **PASS**.
- Required FAIL/BLOCKED/NOT TESTED: **NONE**.
- WAIVED: **DH, Voxy, FSR, XeSS**.
- OUT OF TARGET: **NRD**.
- OUT OF SCOPE: **complete end-to-end GPU frame timing**.

**FINAL GATE disposition: DONE.**
