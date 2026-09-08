# AER-083 denoiser-family A/B

Use the same candidate for A and B. Both runs enable render-graph execution and
keep POST generated barriers disabled. A uses legacy denoiser barriers; B uses
`engine.denoiserBarriersV2=true`.

Close Minecraft and Modrinth, then run:

```powershell
.\scripts\agent\denoiser-barrier-ab.ps1 -Mode A
```

In `prova`, select SVGF by disabling DLSS-RR and NRD. Load the same disposable
world used for both runs. Exercise the first frame/reset, at least 30 seconds
stationary accumulation, movement, camera motion, return to menu and world reload.
Exit Minecraft and Modrinth and collect the printed directory:

```powershell
.\scripts\agent\denoiser-barrier-ab.ps1 -Action Collect -RunDirectory 'THE_PRINTED_DIRECTORY'
```

Repeat with `-Mode B` and the same route/settings. If the experimental NRD native
backend is available, qualify it in a separate matched A/B pair; absence is
`NOT AVAILABLE`, not PASS.

Collection verifies candidate hash, log freshness, selected path/backend and
active synchronization validation. Review must compare VUID/SYNC-HAZARD sets,
visual stability, temporal accumulation, reset/reload behavior and clean shutdown.
The plan explicitly distinguishes previous history/moments/viewZ/normal from
current outputs and alternates ping/pong according to the existing parity.

This candidate does not change POST, upscaler, path-trace output or AS barriers.
