# AER-012 result

Added the explicit `TemporalResetReason` bitset contract for the legacy reset
causes. This task does not redirect reset ownership or alter reset timing;
per-frame collection and broadcast are deferred to AER-013.

Validation: targeted `TemporalResetReasonTest` PASS.
