# AER-083 path-trace-output A/B

Status: candidate; runtime qualification PENDING.

The candidate covers the two existing Caustica barriers:

1. primary trace writes the continuation queue and current guide images; indirect
   trace reads the queue and, for NRD, selected guides;
2. indirect trace writes current radiance/optional NRD signals and current
   temporal state; reconstruction/upscale and the next frame consume those outputs.

The declaration keeps ReSTIR previous/current distinct. Previous ReSTIR and SHaRC
state are cross-frame imports; current ReSTIR and SHaRC updates are exported for
the next frame. Optional viewZ and NRD image declarations follow the selected
runtime path. Both generated barriers retain the legacy all-commands,
memory-read/write scope and occur at the same command boundaries.

Use one same-JAR pair with DLSS-RR forced so color, depth, motion, normal,
diffuse/specular albedo and specular motion outputs are all consumed. NRD remains
NOT AVAILABLE. Close Minecraft and Modrinth before each Start:

```powershell
.\scripts\agent\path-trace-barrier-ab.ps1 -Mode A
```

Exercise the established route: first frame, 30 seconds stationary, movement and
camera rotation, menu, reload the same world, then exit. Collect the printed path:

```powershell
.\scripts\agent\path-trace-barrier-ab.ps1 -Action Collect -RunDirectory 'PRINTED_DIRECTORY'
```

Repeat with -Mode B and the identical route. Both modes enable graph execution,
disable generated POST/denoiser/upscaler barriers, disable frame generation and
enable synchronization validation. They differ only in
engine.pathTraceBarriersV2.

Collect requires unchanged JAR hash, fresh logs, the requested path marker,
successful DLSS-RR execution and active synchronization validation. Acceptance
requires no new VUID/SYNC finding and no visual, temporal, reset or reload
regression. Shader math, ReSTIR/SHaRC algorithms and AER-084 remain unchanged.
