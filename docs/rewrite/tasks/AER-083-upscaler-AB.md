# AER-083 upscaler-family A/B

Status: candidate; runtime qualification PENDING.

Scope: native blit entry and rrOutput export to exposure/post. DLSS-RR, FSR
and XeSS SDK internals (history, layouts and synchronization) retain SDK ownership.
No generated cross-frame SDK-history dependency is claimed. Current color,
depth/motion and DLSS guides are separate read-only inputs; rrOutput is a distinct
current-frame output. Native blit is TRANSFER; SDK evaluation is EXTERNAL.
Entry barriers belonging to trace/denoiser remain with those families.

Run an independent same-JAR pair for each available backend. Start with NATIVE,
then DLSS_RR. FSR and XESS natives are absent from this build: NOT AVAILABLE,
never qualify their fallback as a successful backend run.

Close Minecraft and Modrinth before each Start:

```powershell
.\scripts\agent\upscaler-barrier-ab.ps1 -Mode A -Backend NATIVE
```

Launch prova. The script fixes backend selection via JVM properties for both runs,
enables SVGF for the native path and disables frame generation. Do not change
quality/configuration during the pair. Use the same world and route: first frame,
30 seconds stationary, movement/camera rotation, menu and world reload, then exit.

```powershell
.\scripts\agent\upscaler-barrier-ab.ps1 -Action Collect -RunDirectory 'PRINTED_DIRECTORY'
.\scripts\agent\upscaler-barrier-ab.ps1 -Mode B -Backend NATIVE
```

Collect B after the same route. Repeat the pair with -Backend DLSS_RR.
Both modes enable graph execution, keep generated POST/denoiser barriers off,
and differ only in engine.upscalerBarriersV2. Both enable synchronization validation.

Collect verifies the unchanged JAR hash, log freshness, successful backend/path
marker and synchronization validation. Qualification additionally requires review
of all backend markers (no unmatched switches/fallback), settings/workload parity,
VUID/SYNC findings and explicit visual/temporal feedback. No new Vulkan finding
is accepted; SDK baseline findings must be attributed, not silently ignored.
POST and denoiser prior qualification remains unchanged. Trace outputs and AER-084
are outside this step.
