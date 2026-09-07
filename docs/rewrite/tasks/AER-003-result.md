# AER-003 Result

## Pre-plan

1. Add one development-only gate holder under the suggested `rewrite` package.
2. Keep all seven gates default-false and out of `CausticaConfig`/the options UI.
3. Do not wire any gate into `RtComposite` or another runtime path in this task.
4. Verify the class independently and confirm there are no consumers yet.

## Summary

Added the seven roadmap migration gates as system-property-backed development switches.
They are intentionally isolated from user configuration and currently have no runtime consumers,
so the reference execution path remains structurally untouched.

## Files changed

- `src/main/java/dev/comfyfluffy/caustica/rewrite/RewriteGates.java`
- `docs/rewrite/MIGRATION_STATE.yaml`
- `docs/rewrite/tasks/AER-003-result.md`

## Behavior changes

None. No renderer code reads the new gates yet.

## Invariants

- GPU-001..004: unchanged; no resource/lifetime code touched.
- TMP-001..004: unchanged; no temporal code touched.
- RST-001..004: unchanged; no ReSTIR code or shader touched.
- LOD-001..007: unchanged; no LOD code touched.
- REC-001 / UPS-001..002: unchanged.
- CMP-001..002: unchanged.

## Validation

- V0: PASS (`git diff --check` + staged diff check)
- V1: NOT AVAILABLE in current sandbox (Gradle distribution unavailable; baseline)
- V2: NOT REQUIRED by AER-003
- V3: NOT REQUIRED by AER-003
- Standalone Java compile/default-false probe: PASS

## Follow-ups

AER-004 characterization tests are the next task; no architectural refactor may begin before
GATE-0 is satisfied.
