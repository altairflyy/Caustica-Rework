# GATE-8 frame-stats recovery

The first performance run exposed a pre-existing instrumentation contract bug,
not a render-graph or queue-scheduling failure. With `caustica.rt.frameStats=true`,
terrain light-grid publication called `stage("terrain.lightGridPublish")`, but the
profile registry did not contain that name and deliberately rejected it.

The recovery registers every existing literal production stage/counter that was
missing, including graph-split trace stages and optional backend stages. It changes
only opt-in profiling metadata; renderer execution, queue synchronization, shader
math and normal frame behavior are unchanged.

`RtFrameStatsRegistrationTest` scans literal production calls and verifies them
against the profile registry, preventing another workload-dependent profiler crash.
The failed A run is discarded and cannot be used as performance evidence.
