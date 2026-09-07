# AER-013 result

Added the `TemporalState` coordinator. It collects reset bits, snapshots one
immutable `FrameContext` per frame index, broadcasts an immutable request, and
retains reasons if the consumer fails. It owns no backend resources and does
not alter legacy reset timing or destinations.

Validation: targeted `TemporalStateTest` pending.
