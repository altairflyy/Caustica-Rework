# AER-050 result

## Pre-audit and plan

The legacy reconstruction seam is `RtComposite.reconstructFrame(...)`. It
selects DLSS-RR or SVGF and always hands the upscale stage a non-null image,
while each implementation consumes materially different inputs. A generic
backend request therefore models the real split without inventing a union of
unused guides and controls.

This `CONTRACT_ONLY` task adds the lifecycle and shared result contracts only.
Runtime selection, reset routing and resource ownership remain unchanged until
AER-051 and AER-052 implement and adopt the two backends.

## Validation

- Targeted `ReconstructionBackendTest`: PASS.
- V1 `validate-fast.ps1`: PASS, baseline-equivalent exact 4/4 canonical
  failures; characterization 7/7 PASS; 177 tests executed.
- V2 `validate-build.ps1`: PASS with the same exact baseline comparison.
- V0 `git diff --check`: PASS (line-ending conversion warnings only).

## Acceptance evidence

- `ReconstructionBackend<I>` exposes availability, reset, execution and
  destruction without prescribing a shared request shape.
- `ReconstructionResult` requires the non-null downstream image and records
  whether reconstruction completed, matching the legacy fallback seam.
- The focused test compiles a backend-specific request and exercises every
  lifecycle operation.
- This is intentionally contract-only: no dead facade is claimed as runtime
  integration, and AER-051/AER-052 remain responsible for implementation and
  adoption.
- No shader, reconstruction math, selection, reset timing, resource ownership,
  tuning or hot-path wait-idle changes were made.
