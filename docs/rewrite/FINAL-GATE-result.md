# FINAL GATE result

## Disposition

Canonical requirements remaining in the agreed target: `PASS`.
FINAL state: `DONE`.

- NRD: **OUT OF TARGET / NOT REQUIRED**, not PASS or waiver.
- Complete end-to-end GPU frame-time Average/P95/P99: **OUT OF SCOPE / NOT REQUIRED**, not PASS or waiver.
- Authorized waivers, exactly: **DH, Voxy, FSR, XeSS**.

## Canonical requirement audit

| Exact FINAL requirement | Status | Evidence |
| --- | --- | --- |
| Functional features are preserved where the reference supported them | **PASS / WAIVED** | All required targets are qualified; only DH/Voxy/FSR/XeSS are waived. NRD is explicitly out of target. |
| `Vulkan resta backend` | **PASS** | Vulkan remains the sole rendering backend. |
| `Iris non è dependency` | **PASS** | No Iris dependency exists. |
| `DH/Voxy sono adapters` | **PASS** | Neutral adapter/provider boundaries remain. |
| ``RtComposite` non è più il proprietario universale` | **PASS** | Significant GPU ownership is delegated. |
| `GPU lifetime centralizzato` | **PASS** | Exact-token, frame-tail and shutdown-quiescence authorities cover their domains. |
| `AS lifetime centralizzato` | **PASS** | `AccelerationStructureManager` is the authority. |
| `temporal reset centralizzato` | **PASS** | `TemporalState` is the authority. |
| `pipeline esplicita` | **PASS** | Explicit pass/pipeline contract is runtime-wired. |
| `Render Graph deriva dalla pipeline verificata` | **PASS** | `FrameGraph` derives from `FramePipeline`; `GraphExecution` executes validated order. |
| `nessun nuovo waitIdle hot path` | **PASS** | REMEDIATION-08 relocates the existing shutdown wait only. |
| `nessun stale geometry cross-dimension` | **PASS** | World/dimension invalidation and qualified transition evidence remain intact. |
| `nessuna UAF GPU nota` | **PASS** | Central retirement plus REMEDIATION-08 pre-owner quiescence closes identified lifetime gaps. |
| `nessun device-lost riproducibile` | **PASS** | No reproducible device loss in the qualified candidate. |
| `nessun LOD hole persistente` | **PASS / WAIVED SCOPE** | No persistent hole in qualified base terrain; DH/Voxy provider runtime is explicitly waived. |
| `nessuna coarse/fine duplicated geometry pubblicata` | **PASS / WAIVED SCOPE** | Publication invariants pass; DH/Voxy provider runtime is explicitly waived. |
| `nessun componente src/main introdotto dal rewrite e richiesto dall'architettura resta morto/non referenziato` | **PASS** | Every required component has a production consumer. |
| `nessuna ownership migrata rimane duplicata nel legacy` | **PASS** | Repository-wide ownership audit found none. |
| `nessuna API minima obbligatoria è mancante` | **PASS** | Required boundaries are implemented. |
| `i dev gate rimasti hanno ancora due implementazioni reali oppure vengono rimossi in AER-092` | **PASS** | Obsolete gates were removed. |
| `il percorso legacy rimosso in AER-091 non è ancora raggiungibile` | **PASS** | `GraphExecution` is sole execution authority. |
| ``RtComposite` contiene soltanto orchestration/delega prevista dal target` | **PASS** | Direct significant GPU ownership is none. |
| `GPU/AS/temporal/scene/pipeline/render-graph authority hanno un owner univoco` | **PASS** | Single-owner/dependency audit passes. |
| `FINAL_REPORT.md descrive il codice realmente committato e validato` | **PASS** | Report targets candidate `98ce86e` with exact evidence boundaries. |
| Performance thresholds for metrics remaining in scope | **PASS** | CPU dispatch, composite GPU duration and VRAM are within limits; full-frame timing is out of scope, not PASS. |

## Runtime and validation

Required PASS: Overworld, Nether, End, entities, particles/weather, water/glass, LabPBR, ReSTIR, SHaRC, SVGF, DLSS-RR, HDR, SDR and clean shutdown. Remaining required runtime NOT TESTED: **NONE**.

ReSTIR evidence: `lightCount=10596`, non-zero light buffer, `restirMode=1`, valid previous/current reservoirs and `restirPathOn=true`. LabPBR evidence: `spec=1906`, `normal=1899`, `labPbrEmission=19636`.

- Candidate: `98ce86e555376a671fcbc753d551e8a9d55cbc7d`.
- Validation: 266 total, 262 passed, exact 4 canonical failures, 0 unexpected, characterization 7/7 and V2 PASS.
- Closure diff: documentation/tracker only.

## Performance and accounting

- Matched CPU dispatch: **PASS**.
- Composite command-buffer GPU duration: **PASS**.
- VRAM: **PASS**, +2.06% against +10%.
- Complete end-to-end GPU frame timing: **OUT OF SCOPE / NOT REQUIRED**, not PASS.
- Required FAIL/BLOCKED/NOT TESTED: **NONE**.
- WAIVED: **DH, Voxy, FSR, XeSS**.
- OUT OF TARGET: **NRD**.

**FINAL CLOSED.**
