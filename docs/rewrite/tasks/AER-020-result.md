# AER-020 result

Created and adopted the provider-neutral `rt/lod/LodMesh` record. The nested
DH type was removed; DH capture, Voxy reflection conversion, and terrain
planning/decoding now share the same fields and record accessors. Existing
mesh bytes, metadata, ordering, reuse and fallback semantics remain unchanged.

Validation:

- targeted `RtDistantHorizonsTerrainTest` and characterization: PASS;
- V1 `validate-fast.ps1`: `BASELINE-EQUIVALENT`, exact 4/4 baseline failures;
  characterization 7/7 PASS;
- V2 `validate-build.ps1`: PASS;
- `git diff --check`: PASS;
- forbidden-change audit: PASS; no shader, math, tuning, AER-021 or AER-020
  follow-up changes.

State: DONE.
