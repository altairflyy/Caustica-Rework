# Final integration recovery: post-processing owner

User-authorized continuation of the final architecture recovery, one owner only.
Scope extends the narrow original AER-090 migration to the remaining post owner
identified by FINAL_REPORT; no shader/algorithm work is included.

PostProcessing now owns exposure, display pipeline, SDR/HDR images and records
the former postPresentFrame body. Composite delegates without parallel fields.
Keep release/create and image/pipeline destruction split at the old call sites,
because other owners' lifetime operations occur between them. The HDR-written
notification stays immediately after display dispatch and before copy/barrier
failure, preserving the old partial-frame failure behavior.

Targeted characterization checks ownership, lifecycle ordering, formats,
descriptor binding order and record/notification/barrier ordering. Existing POST
barrier-plan tests remain unchanged.

Validation:

- `validate-build.ps1`: PASS; V0 + V1 + V2 demonstrated in one run.
- V1: 248 tests, exact 4/4 canonical baseline failures; characterization 7/7 PASS.
- V2: Gradle build PASS; NGX shim present (83,968 bytes).
- `git diff --check`: PASS.
- Forbidden-change audit: no shader/math, synchronization, feature-selection,
  tuning or new `waitIdle` changes.
