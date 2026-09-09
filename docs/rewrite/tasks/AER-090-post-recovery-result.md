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
barrier-plan tests remain unchanged. Validation pending.
