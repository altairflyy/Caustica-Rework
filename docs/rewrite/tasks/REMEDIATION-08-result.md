# REMEDIATION-08 — Shutdown quiescence convergence

Status: COMPLETED

## Production change

- Preserved producer shutdown ordering: `RtTerrain.shutdown(ctx)` drains terrain/LOD work, then
  `RtWorkerPool.INSTANCE.shutdown()` stops worker production.
- Split `RtGpuExecutor` shutdown into `quiesceForOwnerShutdown()` and
  `destroyAfterQuiescence()`.
- Quiescence first closes submissions, stops and joins the executor thread, performs the single
  global `ctx.waitIdle()`, flushes deferred destroys, and leaves executor infrastructure alive.
- `CausticaClient` establishes that quiescence before entity, composite, backend, presentation,
  and native-runtime teardown.
- Final `RtContext.destroy()` destroys executor command-pool/timeline infrastructure only after
  owner teardown and does not issue a second global wait.
- Retirement requested after quiescence executes immediately at the proven device-idle boundary;
  it is not queued to the stopped executor.
- The global wait covers prior persistent-frame-tail submissions without changing normal
  `FrameTailRetirement` behavior.

## Safety and ownership

- New hot-path waits: none.
- Global shutdown waits added: none; the existing executor shutdown wait was moved to the explicit
  pre-owner boundary.
- Normal runtime exact-token and frame-tail retirement: unchanged.
- Duplicate shutdown authority: none.
- Unsafe owner teardown paths identified after the change: none.
- Shader, renderer math, baseline, validator, roadmap, FINAL report and tracker changes: none.

## Tests

- Behavioral: `GpuShutdownState` rejects submissions after shutdown starts, requires completed
  quiescence before final infrastructure destruction, and makes both phases idempotent.
- Structural shutdown-order characterization: producer drain precedes quiescence; executor
  stop/join precedes wait/flush; owner teardown precedes final executor destruction; final context
  destruction contains no second wait.
- Targeted `GpuShutdownQuiescenceTest`: PASS (3/3).

## Validation

- `validate-fast.ps1`: PASS, baseline-equivalent.
- `validate-build.ps1`: PASS.
- Total tests: 266.
- Passed: 262.
- Canonical failures: exact 4/4.
- Unexpected failures: 0.
- Rewrite characterization: 7/7 PASS.
- V2 build: PASS.

## Remaining scope

This remediation closes the shutdown-quiescence GPU lifetime gap only. It does not change the
current `FINAL: PENDING` state or resolve remaining runtime qualification and complete GPU
frame-time evidence gaps.
