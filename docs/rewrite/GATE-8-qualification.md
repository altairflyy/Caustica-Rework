# GATE-8 qualification

Gate closure needs one final-candidate Vulkan smoke and one matched performance
pair. Validation runs and performance runs are intentionally separate because the
validation layer changes timing.

## Performance A/B

Both modes use the same installed JAR, profile, DLSS-RR backend and frame-stats
instrumentation. Frame generation and optional native FSR/XeSS/NRD paths are off.
Mode A uses the legacy pipeline/barrier paths; mode B enables the render graph and
all four qualified generated-barrier families. AER-084 queue scheduling is common
to both paths and has no per-frame allocation or new synchronization operation.

Run A, collect it, then repeat with B:

```powershell
.\scripts\agent\gate8-performance-ab.ps1 -Mode A
.\scripts\agent\gate8-performance-ab.ps1 -Action Collect -RunDirectory '<printed A directory>'

.\scripts\agent\gate8-performance-ab.ps1 -Mode B
.\scripts\agent\gate8-performance-ab.ps1 -Action Collect -RunDirectory '<printed B directory>'
```

For each run, load the same world and fixed viewpoint, wait 30 seconds, then do
not move or rotate for at least 60 seconds. Exit Minecraft and Modrinth before
collection. The collector verifies log/CSV freshness and unchanged JAR hash,
discards 300 warm-up frames, requires at least 600 measured frames, and reports
average, P95 and P99 frame-envelope time. Acceptance uses the roadmap threshold:
candidate P99 must not regress by more than 10 percent.

## Final Vulkan smoke

Use the final JAR with graph execution and all available generated-barrier families
enabled, synchronization validation active, and DLSS-RR fixed. Exercise world load,
stationary accumulation, movement/rotation, menu, world reload and clean shutdown.
Compare the resulting VUID/SYNC set with the accepted AER-083 evidence. Existing
DH vertex-input and NGX DLSSD WAW findings may be attributed only when identical;
no new finding is accepted.
