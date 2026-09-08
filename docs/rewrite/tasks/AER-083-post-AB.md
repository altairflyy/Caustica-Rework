# AER-083 post-image candidate: A/B procedure

This is a first-family candidate, not GATE-8 closure. The installed candidate
keeps the old path by default. Both A and B enable graph execution, so the only
difference between runs is `engine.postBarriersV2=false` (A) or `true` (B).

## Run A

Close Minecraft and Modrinth completely. In a PowerShell terminal at the repository:

```powershell
.\scripts\agent\post-barrier-ab.ps1 -Mode A
```

Launch `prova` from the prepared launcher. Use a disposable test world. Record
the starting dimension, location, resolution, exposure mode and HDR setting.
Load the world, stay stationary 30 seconds, move/stream chunks, return to menu
and reload. Exercise automatic and manual exposure, plus HDR/SDR where supported.
Keep settings and the route identical for B. Note any visible difference or crash.
Exit Minecraft and Modrinth before collecting.

The start script prints a unique evidence directory. Collect it before B:

```powershell
.\scripts\agent\post-barrier-ab.ps1 -Action Collect -RunDirectory 'THE_PRINTED_DIRECTORY'
```

## Run B

```powershell
.\scripts\agent\post-barrier-ab.ps1 -Mode B
```

Repeat the same scenarios, exit both applications and collect the B directory.
Use the same JAR for both runs. Collection verifies hash, log freshness, runtime
path marker and explicit synchronization-validation activation; it does not
declare PASS merely because a file exists or because the game starts.

## Acceptance and boundaries

- Compare A/B VUID and SYNC-HAZARD messages, not just absolute error counts.
- Confirm runtime markers for automatic/manual exposure and supported HDR modes.
- Confirm visual equivalence, world reload and no new device/lifetime failures.
- The preflight proves layer configuration only, not Minecraft synchronization.
- Validation instrumentation changes timing. P99 qualification needs separate
  comparable runs without validation overhead before GATE-8 can close.
- Only post images and their directly required exposure-buffer dependencies are
  included. Entry synchronization from upscale and every other resource family
  remain legacy. Denosier/upscaler/trace families and AER-084 await post A/B PASS.
- Environment variables are scoped to the launched Modrinth process; no launcher
  database, global Java settings or gameplay configuration is changed. Close that
  launcher after each run; a normal subsequent launcher start has default flags.
