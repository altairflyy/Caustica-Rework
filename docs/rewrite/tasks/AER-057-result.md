# AER-057 result

## Runtime quarantine

NRD/REBLUR is retained only as an experiment behind
`rt/reconstruction/experimental/nrd/ExperimentalNrdBackend`.

- Source present: yes.
- Native shim source/build path present: yes.
- Runtime path: retired and hard-disabled by the quarantine boundary.
- Production baseline: no.
- User/config/native availability cannot select it.
- `RtComposite` has no direct dependency on `RtNrdDenoiser`; retained lifecycle and
  execution calls are reachable only through the quarantine boundary.

## Validation

- `ExperimentalNrdBackendTest` and rewrite characterization: PASS.
- V1: BASELINE-EQUIVALENT, exact canonical 4/4 failures; characterization
  7/7 PASS; 184 tests executed.
- V2: PASS.
- `git diff --check`: PASS.
- Shader/math changes: none.
- Optional NRD native binary: unavailable in this build environment; this does
  not affect the retired runtime path.
