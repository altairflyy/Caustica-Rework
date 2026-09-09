# Final rewrite audit — 2026-09-09

## Decision and scope

**FINAL: NOT QUALIFIED (PENDING). AER-093: BLOCKED.**

Audited production revision: `fd7ce60` on `rewrite/aer`. Frozen reference:
`reference/validated-caustica-0.2.0` (`3c54fc201f93598246db62ebb1947dfb1e274e92`).
This is an evidence report, not a claim that the final target is complete.

The user explicitly excluded further DH/Voxy and FSR/XeSS investigation and
qualification on 2026-09-09 and authorized agent-operated tests. These paths
are **WAIVED FROM THIS CLOSURE ATTEMPT**, not newly qualified or fixed. The
DH/Voxy bug remains open. No provider/backend work was performed for this audit.
The exception does not waive unrelated architecture or performance acceptance.

## Integration audit

Paths below are relative to `src/main/java/dev/comfyfluffy/caustica/`.

| Boundary | Evidence at audited revision | Assessment |
| --- | --- | --- |
| FrameContext | `rt/RtComposite.java:1478` constructs it in production; delta ticks are multiplied by `0.05f`; scene generation uses the explicit unversioned constant, not light/material generation. | Runtime integrated; legacy scene-version gap remains explicit. |
| TemporalState | `rt/RtComposite.java:531,645,661` constructs, collects, snapshots and broadcasts; `rt/frame/TemporalState.java` owns the pending bitset and per-index snapshot. Consumer exception precedes the clear. | Runtime integrated; no second authoritative pending queue identified. |
| ReSTIR | `rt/lighting/RestirSystem.java` owns `RestirHistory`; composite consumes the system and advances after accepted command execution. | Migrated history ownership integrated. |
| SHaRC | `rt/lighting/SharcRadianceCache.java` is the singleton domain owner used by composite. | Migrated cache seam integrated. |
| SVGF | `rt/reconstruction/SvgfReconstructionBackend.java:30` owns `SvgfResources`. | Migrated history resources integrated; this is not a fresh visual qualification. |
| Scene | `rt/RtComposite.java:1619,1623,1735` uses `SceneAssembler` for contributions and TLAS input. | Runtime integrated. |
| AS/retirement | `rt/RtContext.java:91` constructs `DeferredDeletionQueue` and `AccelerationStructureManager`; the manager owns the frame TLAS ring and delegates retirement. | Runtime integrated. Manager Javadoc still incorrectly says it contains no lifetime state. |
| Upscaler lifetime | `UpscalerRuntime` owns the migrated FSR/XeSS/native backend instances rather than composite. | AER-090 scoped migration retained; optional backends not requalified. |
| Graph execution | `rt/graph/GraphExecution.java` binds the production pass list and always returns its graph cursor; composite instantiates and uses it. | Graph is the selected runtime path. |
| Barrier/queue execution | Four qualified emitter families have no manual fallback; `rt/RtGpuExecutor.java:54` instantiates `QueueDependencyScheduler`. | Runtime seams integrated; tests do not prove device-wide safety. |
| Development gates | `RewriteGates` removed in AER-092; no production `engine.*V2` references remain. Historical A/B Start operations reject before side effects. | PASS. |
| Remaining linear runner | `rt/frame/FramePipeline.java:23,30,50` still ships `execute`, `begin` and `Cursor`. Production graph consumes only callback bindings; the old runner is used by tests. | Residual alternate execution API, not an active runtime branch. Requires final dead-code disposition. |
| Composite orchestration-only target | `rt/RtComposite.java:1059` still owns resize/allocation; `:1150` onwards allocates trace, continuation, display and guide resources; `:2359` destroys them. FG/presentation lifetimes also remain (`:3222` onwards). | The strict FINAL orchestration-only acceptance is not demonstrated. Narrow AER-090 acceptance does not establish this broader claim. |

The audit found concrete blockers before an exhaustive all-component sign-off.
It does not falsely certify the absence of every other integration gap. No
ownership recovery was mixed into this DOC_ONLY task. AER-090 explicitly permits
removal only of already-migrated ownership: extending it to presentation/trace
owners needs a bounded recovery plan, not an incidental refactor in this report.

## Reference versus rewrite evidence

| Area | Reference 0.2.0 | Rewrite evidence | Final qualification |
| --- | --- | --- | --- |
| Feature parity | Baseline proves Vulkan startup and DLSS-RR initialization. | Historical GATE-7 playthrough covers Overworld/Nether/End/rain/snow; AER-083 family runs and AER-091 DLSS-RR smoke cover their recorded candidates. | Historical evidence only; not a complete final-candidate feature matrix. |
| Unit tests | Historical 123 tests/9 failures; EOL correction establishes the exact 4 genuine failures. | Current 246 tests, 242 pass, exact 4 canonical failures; characterization 7/7. | V1 baseline-equivalent PASS. |
| Build | Reference NGX shim rebuilt, optional SDK artifacts absent. | V2 PASS; NGX shim 83968 bytes; optional FSR/NRD/XeSS artifacts still absent. | PASS under current build policy. |
| CPU | No matched frozen-reference timing captured in available baseline evidence. | Historical GATE-8 CPU envelope measurements below. | NOT MEASURED reference vs final. |
| GPU average | No matched reference GPU timestamp measurement supplied. | CPU envelope is not GPU time. | NOT MEASURED; +5% gate unproven. |
| P95/P99 | No matched reference-vs-final run. | Historical graph-toggle pair below. | Not a final reference comparison. |
| VRAM steady state | No matched reference sample. | No matched final sample. | NOT MEASURED; +10% gate unproven. |
| BLAS counts | No matched reference workload counts. | Runtime diagnostics exist, but no matched reference/final dataset. | NOT MEASURED. |
| LOD rebuild/reuse | No matched reference/final dataset. | Further DH/Voxy work explicitly excluded. | WAIVED for this attempt, not PASS. |
| Device compatibility | Local Windows Vulkan/DLSS-RR baseline only. | Local historical smoke only; no multi-device qualification. | Limited to demonstrated host/backend. |
| HDR/SDR | No complete matched matrix. | Historical DLSS-RR smoke uses SDR. | HDR not demonstrated by those runs. |

Historical GATE-8 same-JAR toggle measurements, 600 active frames each:

| CPU-side metric | Legacy A | Generated B | Delta |
| --- | ---: | ---: | ---: |
| Average envelope | 7.3314 ms | 8.3637 ms | +14.08% |
| P95 envelope | 9.214 ms | 9.699 ms | +5.26% |
| P99 envelope | 10.472 ms | 11.207 ms | +7.02% |

Source: `build/rewrite-validation/GATE-8-performance/`
`A-434578dd0a7b4bfb8110c4379071fc17/metrics.json` and
`B-f178a7f8e8864419a87d2c470a196201/metrics.json`.
Both use hash `D6B31873072FDEBA92F71621E087BEEC0A6183EA6346E941B350C3FEAEC7FD9A`.
These are neither the frozen original nor the final revision. The +14.08% CPU
average is retained transparently; it cannot establish a GPU regression or PASS.

## Runtime attempt on behalf of the user

Installed the built candidate as the sole active Caustica JAR in `prova`:
`caustica-0.2.8-aer092-candidate.jar`, SHA-256
`0516EC4E3B265FF702EDA8EAD2BDEF92544FE54229253D6827CC9DD0E552DCF0`.
The AER-091 candidate is recoverable in the profile's `.codex-jar-backups/`
as `caustica-0.2.8-aer091-before-aer092.jar`. No duplicate active mod installed.

Prepared synchronization validation using the current smoke harness:
`build/rewrite-validation/GATE-8-validation/B-d1e898ac614342d3a09ae4d458261bd3`.
Modrinth launched, but Windows Computer Use returned
`Computer Use app approval timed out` when inspecting the launcher.
No game input, route, accumulation, reload or fresh runtime PASS is claimed.
The run directory contains only the manifest and layer settings, not runtime
evidence. Launcher remains prepared; it is not evidence of a Minecraft launch.
The computer-use skill's permission boundary was respected, not bypassed through
another input mechanism. No user playthrough is demanded while the user is away.

Latest previously accepted smoke is AER-091:
`build/rewrite-validation/GATE-8-validation/B-883bda6e50ec4e5c8ed1515f9e534c92`.
It is DLSS-RR/SDR evidence, not SVGF or HDR evidence. Its accepted findings include
DH vertex-input messages, NGX DLSSD WAW hazards and a shutdown child-object leak
report. Baseline equivalence must not be described as zero validation errors.

## Known changes and unresolved items

- ReSTIR boiling/flickering remains a known baseline issue, not repaired here.
- Four canonical parallax/water shader test failures remain exactly unchanged.
- DH/Voxy issue and FSR/XeSS qualification remain excluded as requested.
- NRD native runtime and HDR are not qualified by the fresh attempt.
- The only shader file differing from the frozen tag is `world.rahit.slang`:
  `MaterialHeader materialHeader` is zero-initialized. The full rewrite therefore
  must not be described as literally zero shader diff. AER-092 has zero shader diff.
- Demonstrated improvements are runtime ownership seams, explicit graph/barrier
  execution and regression coverage; no measured final speed/VRAM gain is claimed.

## Required closure recovery

1. Resolve the strict orchestration target versus remaining composite-owned
   resources, and remove/dispose of the obsolete linear runner under a scoped
   recovery task. Preserve already-qualified timing, masks and ownership.
2. Complete final-candidate smoke once desktop authorization is available to the
   agent; do not count the prepared-only run as evidence.
3. Record comparable reference/final GPU, percentile, VRAM and BLAS metrics with
   validation disabled for timing. Keep optional excluded paths excluded.
4. Re-audit all final requirements, validate, then commit an actual gate closure.

DH/Voxy and FSR/XeSS are not the reasons this attempt cannot claim FINAL PASS.
