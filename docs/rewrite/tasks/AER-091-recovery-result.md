# AER-091 final-audit recovery

Pre-audit: the production graph uses FramePipeline only as immutable callback
bindings. The old execute/begin/Cursor API has no production caller; tests alone
keep it alive. Remove that runner, retaining the production binding container.
Move order/frame-identity/incremental tests to GraphExecution rather than delete
their invariants. Keep graph callback failure/advancement characterization.

No runtime callback, resource lifetime, shader, synchronization or tuning changes.
Validation: targeted FramePipeline/GraphExecution/FrameGraph/LegacyPathRemoval
tests PASS; validate-build.ps1 V0/V1/V2 PASS, 246 tests, exact 4/4 canonical
failures and characterization 7/7 PASS. Diff review and forbidden-change audit
PASS: no shader/math, runtime callbacks, barriers or resource changes.

V3: agent-operated DLSS-RR/SDR smoke completed with synchronization validation:
world load in Nether, F3+A reset, short movement input, 64-block teleport with
camera rotation/streaming, 30 seconds stationary, menu, world reload, shutdown.
Evidence: build/rewrite-validation/GATE-8-validation/
B-655481cb033c4a4498bb4e9570a66c7d. Runtime markers and layer activation verified.
The three distinct validation error headers exactly match the earlier AER-091
run: DH vertex input, NGX WAW, shutdown child-object leak. Not zero-error PASS.
Optional DH/FSR/XeSS qualification is excluded by the user.

Candidate SHA256: B49F5C69580E955D7A0008B25D32B37FE29129AF74237171FB11D5AD0C489B56.
One active candidate in prova; previous AER-092 JAR retained outside mods.
Fullscreen and pauseOnLostFocus restored to true after testing.

An initial remote capture after F11 showed vertical stripes. The repeat run at
the same 854x480 display extent did not reproduce them, including Minecraft's
own screenshot 2026-09-09_09.10.37.png. Cause remains unproven; no speculative
rendering fix applied. This observation does not establish a runner regression.

State: DONE. AER-093 remains blocked by separate final architecture/performance
acceptance, not by this runner recovery. Desktop authorization now works.
