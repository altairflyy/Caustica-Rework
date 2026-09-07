# GATE-1 result

Temporal/state extraction gate passed after recovery of the ReSTIR
characterization. `RestirHistory` preserves the reference addressing contract:
the previous half is `writeIndex ^ 1`, the current half is `writeIndex`, and
parity advances only after command execution is accepted.

Validation:

- targeted `RestirHistory` / `RestirReservoirMathTest` / characterization: PASS
- V1 `validate-fast.ps1`: PASS, exact frozen 9-failure baseline
- V2 `validate-build.ps1`: PASS
- V3 Vulkan smoke: unavailable; no configured smoke host/script in this checkout
- shader/math diff: none; ReSTIR and denoiser shader sources unchanged

AER-012 through AER-016 are DONE. AER-020 was not started.
