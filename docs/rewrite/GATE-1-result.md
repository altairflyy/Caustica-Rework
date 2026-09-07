# GATE-1 result

Temporal/state extraction gate passed after recovery of the ReSTIR
characterization. `RestirHistory` preserves the reference addressing contract:
the previous half is `writeIndex ^ 1`, the current half is `writeIndex`, and
parity advances only after command execution is accepted.

Validation:

- targeted `RestirHistory` / `RestirReservoirMathTest` / characterization: PASS
- V1 `validate-fast.ps1`: historical PASS against the former 9-observation
  set; the canonical characterization baseline is now the exact 4-failure
  set after correcting the CRLF-sensitive test helper.
- V2 `validate-build.ps1`: PASS
- V3 Vulkan smoke: unavailable; no configured smoke host/script in this checkout
- shader/math diff: none; ReSTIR and denoiser shader sources unchanged

AER-012 through AER-016 are DONE. AER-020 was not started.

The nine-failure count above is retained as historical gate evidence. The
five EOL-dependent observations were characterization artifacts; no
production or shader behavior was changed. Current migration status is
authoritative in `MIGRATION_STATE.yaml`.
