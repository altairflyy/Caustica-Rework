# AER-014 result

Extracted the ReSTIR two-buffer history ownership into `RestirHistory`.
Allocation, clear, device-address selection, parity advance, and destruction
remain semantically identical; shader sources and reservoir mathematics are
unchanged. No new synchronization primitive was introduced.

Validation: `RestirReservoirMathTest` PASS; characterization updated to inspect
the extracted `RestirHistory` ownership while preserving the previous/current
address and parity invariant.
