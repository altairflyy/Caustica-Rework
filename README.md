# Caustica Rework

[![Release](https://img.shields.io/badge/release-0.1.2-blue.svg)](https://github.com/altairflyy/Caustica-Rework/releases/tag/rework-0.1.2)
[![License](https://img.shields.io/badge/license-LGPL--3.0--or--later-green.svg)](LICENSE.md)
[![Minecraft](https://img.shields.io/badge/minecraft-26.2-brightgreen.svg)](https://minecraft.net/)
[![Fabric](https://img.shields.io/badge/fabric-0.19.3+-orange.svg)](https://fabricmc.net/)
[![Vulkan RT](https://img.shields.io/badge/vulkan-ray%20tracing-red.svg)](https://www.vulkan.org/)
[![Downloads](https://img.shields.io/github/downloads/altairflyy/Caustica-Rework/total?color=blue)](https://github.com/altairflyy/Caustica-Rework/releases/tag/rework-0.1.2)

**Caustica Rework** is a heavily reworked direct fork and architectural overhaul of [Caustica](https://github.com/ComfyFluffy/Caustica), an experimental hardware ray-traced renderer for Minecraft 26.2's Vulkan backend. It brings native hardware path tracing, NVIDIA DLSS Ray Reconstruction, native SVGF denoising, and HDR presentation to Minecraft while preserving the vanilla UI and gameplay intact.

> ### [Download Caustica Rework 0.1.2](https://github.com/altairflyy/Caustica-Rework/releases/tag/rework-0.1.2)
> Verified release artifact: `caustica-0.1.2.jar` &bull; [Direct JAR Download](https://github.com/altairflyy/Caustica-Rework/releases/download/rework-0.1.2/caustica-0.1.2.jar) &bull; [Installation Guide](#install)

[Download](https://github.com/altairflyy/Caustica-Rework/releases/tag/rework-0.1.2) | [Install](#install) | [Requirements](#requirements) | [Building from Source](#building-from-source) | [Known Limitations](#known-limitations) | [Architecture](#architecture) | [Changelog](CHANGELOG.md) | [Rewrite Documentation](#rewrite-documentation)

This repository is published as a direct GitHub fork of the original [ComfyFluffy/Caustica](https://github.com/ComfyFluffy/Caustica) repository, with development initially based upon the [xysgottaken2/Caustica](https://github.com/xysgottaken2/Caustica) development fork before systematically diverging into a redesigned, decoupled architecture.

The rewrite was undertaken to replace monolithic GPU resource ownership with strict architectural boundaries, explicit frame graphs, robust device quiescence on level transitions and shutdown, and a qualified real-time path tracing pipeline.

![Caustica Rework](docs/gallery/causticafork.png)

## Links

- [GitHub Releases (Download)](https://github.com/altairflyy/Caustica-Rework/releases/tag/rework-0.1.2)
- [Changelog](CHANGELOG.md)
- [Original Upstream Repository (ComfyFluffy/Caustica)](https://github.com/ComfyFluffy/Caustica)
- [Development Base Repository (xysgottaken2/Caustica)](https://github.com/xysgottaken2/Caustica)
- [Discord](https://discord.gg/SeWCjyKu2)
- [Modrinth](https://modrinth.com/mod/caustica)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/caustica/preview)
- [Gallery](docs/gallery.md)

## What changed?

Compared to earlier monolithic iterations of the renderer, Caustica Rework introduces substantial architectural, stability, and rendering improvements:

- **Orchestration vs Ownership (`RtComposite`)**: `RtComposite` has been reduced from a universal GPU resource owner to a lightweight pass orchestrator and delegation coordinator.
- **Dedicated Ownership Domains**: Dedicated, isolated lifecycle boundaries were established for `WorldTraceResources`, `TraceFrameResources`, `FrameGenerationResources`, and `PostProcessing`.
- **Centralized GPU Resource Lifetime**: Strict, proven allocation and retirement tracking via `DeferredDeletionQueue` and `VulkanFrameTailRetirement`, preventing in-flight GPU destruction races.
- **Safe Shutdown & Level Quiescence**: Mandatory GPU device idle waits (`vkDeviceWaitIdle`) prior to tearing down resources during dimension changes or client shutdown, eliminating crashes on exit.
- **Frame Pipeline & RenderGraph**: Explicit macro-pass dependency graph scheduling with automated barrier synthesis across compute, ray tracing, and graphics queues.
- **Hardware Vulkan Ray Tracing Pipeline**: Native Vulkan KHR ray tracing pipeline with modular scene assembly (`SceneAssembler`, `RtScene`, terrain, entities, weather).
- **Dual Reconstruction Backends**: Production support for both proprietary NVIDIA DLSS Ray Reconstruction (via native shim) and a fully native, vendor-agnostic SVGF spatio-temporal denoiser.
- **Advanced Lighting & Radiance Caching**: Integrated spatio-temporal ReSTIR direct lighting reservoir sampling and SHaRC spatial hash radiance cache for stable multi-bounce indirect illumination.
- **LabPBR 1.3 Materials**: Accurate physically based rendering with metallic, roughness, normal maps, and toggleable subsurface scattering.
- **Qualified SDR & HDR Presentation**: End-to-end native HDR10/PQ (BT.2020) presentation path on supported HDR platforms alongside standard calibrated SDR display tone mapping.

## Current qualification

The rewrite followed a formal, gate-driven qualification process verifying architectural integrity, visual accuracy, stability, and safety:

- **Qualified Production Targets (PASS)**:
  - **Visual & Material Accuracy**: Water and stained glass transmission/refraction, LabPBR 1.3 physical materials, and emissive surfaces.
  - **Lighting & Denoising**: Spatio-temporal ReSTIR direct light sampling, SHaRC radiance caching, DLSS-RR, and the native SVGF denoiser.
  - **Color & Presentation**: Native HDR10/PQ swapchain presentation and SDR color pipeline.
  - **Architecture & Safety**: Single-owner resource boundaries, explicit queue barriers, verified GPU lifetime management, clean level transitions, and panic-free shutdown quiescence.
  - **System Performance**: Matched CPU frame dispatch (no CPU regression), composite GPU execution verified with zero regressions, and VRAM consumption within strict gate limits.
- **Benchmarking Scope**: Comprehensive full-frame GPU benchmark measurements are **out of scope / not required** for this functional baseline release.
- **Denoiser Scope**: NVIDIA Real-Time Denoisers (NRD) is experimental, quarantined behind `rt/reconstruction/experimental/nrd/`, and **out of target / not required** for production.
- **Qualification Waivers (DH, Voxy, FSR, XeSS)**: Integration code for Distant Horizons (DH), Voxy LOD, AMD FSR 3, and Intel XeSS is present in the codebase where applicable, but runtime qualification of these paths is covered under authorized waivers and excluded from the production qualification baseline.

## Architecture

```text
RtComposite
├── WorldTraceResources       (AS manager, static/dynamic scene buffers)
├── TraceFrameResources       (per-frame tracing targets, G-buffers)
├── FrameGenerationResources  (optical flow, UI/HUD capture masks)
├── PostProcessing            (SDR tone mapping, HDR10/PQ presentation)
├── Reconstruction Backends   (DLSS Ray Reconstruction, native SVGF)
├── Lighting Systems          (ReSTIR reservoir sampling, SHaRC cache)
├── SceneAssembler            (terrain, entities, particles, weather)
├── TemporalState             (motion vectors, camera history, reset tracking)
└── GraphExecution            (macro-pass dependencies, automatic barrier synthesis)
```

`RtComposite` now functions strictly as an orchestrator rather than a monolithic GPU resource owner. Each rendering domain maintains a single semantic owner responsible for resource creation and disposal. Potentially in-flight GPU resources are retired through explicit frame-tail retirement queues with timeline semaphore synchronization. Before tearing down any resource domain during level unloads or application shutdown, the engine enforces strict GPU device quiescence.

## Rewrite documentation

Full architectural analyses, gate criteria, and task verification records are maintained in the repository for technical review:

- [`FINAL_REPORT.md`](docs/rewrite/FINAL_REPORT.md): Comprehensive summary of the final architecture, gate closures, and qualification evidence.
- [`FINAL-GATE-result.md`](docs/rewrite/FINAL-GATE-result.md): Exact final gate evaluation and sign-off disposition.
- [`MIGRATION_STATE.yaml`](docs/rewrite/MIGRATION_STATE.yaml): Machine-readable tracking of all completed milestones and architectural invariants.
- [`BLOCKERS.md`](docs/rewrite/BLOCKERS.md): Historical record of technical blockers and their architectural resolutions.
- [`docs/rewrite/tasks/`](docs/rewrite/tasks/): Detailed engineering reports for individual AER tasks, remediation milestones, and A/B barrier validations.

## Install

1. **Minecraft & Java Environment**: Ensure you have Minecraft `26.2` installed with Java `25` or newer.
2. **Install Dependencies**: Install [Fabric Loader](https://fabricmc.net/) (`>=0.19.3`) and [Fabric API](https://modrinth.com/mod/fabric-api) (`>=0.145.4+26.2`).
3. **Download Caustica Rework**: Download `caustica-0.1.2.jar` from the [GitHub Releases](https://github.com/altairflyy/Caustica-Rework/releases/tag/rework-0.1.2) page.
4. **Place JAR**: Place `caustica-0.1.2.jar` directly into your Minecraft instance's `.minecraft/mods` directory.
5. **Launch with Vulkan**: Start Minecraft with the Vulkan graphics backend enabled.
6. **Configure Renderer**: Open **Video Settings** in-game to configure Caustica's ray tracing, denoiser, and presentation settings.

## Requirements

### Required
- **Minecraft**: `26.2`
- **Java**: `25` or newer
- **Fabric Loader**: `>=0.19.3`
- **Fabric API**: Supported Fabric API build for Minecraft 26.2 (e.g. `>=0.145.4+26.2`)
- **Vulkan Ray Tracing GPU**: A dedicated graphics card supporting Vulkan 1.2+ and hardware ray tracing via the `VK_KHR_ray_tracing_pipeline` extension:
  - NVIDIA: GeForce RTX 20 series or newer
  - AMD: Radeon RX 6000 series (RDNA 2) or newer
  - Intel: Arc A-Series or newer
- **Operating System**: Windows 10/11 x64 (official release JAR bundles Windows x64 native shims and libraries). Linux x64 is supported when building from source.

### Optional / Feature-Specific
- **NVIDIA DLSS Ray Reconstruction (DLSS-RR)**: Requires an NVIDIA RTX GPU and modern Game Ready or Studio driver.
- **HDR Presentation**: Requires an HDR10-capable display, OS HDR mode enabled (Windows HDR or a native HDR-capable Wayland compositor session on Linux), and an active HDR swapchain.
- **PBR Materials**: Requires a LabPBR 1.3 resource pack (such as [SPBR](https://modrinth.com/resourcepack/spbr)) for metallic, roughness, normal maps, and subsurface scattering.
- **Native Denoiser (SVGF)**: Available out-of-the-box on all supported Vulkan RT hardware as a vendor-agnostic fallback when DLSS is not used.

## Building from source

Developers building Caustica Rework from source require:
- **Java**: JDK 25 or newer
- **Vulkan SDK**: 1.3+ or 1.4+ (providing `glslangValidator`, `slangc`, and `spirv-val` in `PATH` or `VULKAN_SDK`)
- **C/C++ Compiler & CMake**: CMake 3.20+ and MSVC C++ toolchain (Visual Studio / Build Tools)
- **NVIDIA DLSS SDK (Optional)**: Required if bundling proprietary DLSS vendor libraries (`nvngx_dlssd.dll`, `nvngx_dlssg.dll`). Proprietary vendor DLLs are not tracked in Git.

### Build Steps (PowerShell / Windows)

```powershell
# 1. Clone the repository
git clone https://github.com/altairflyy/Caustica-Rework.git
cd Caustica-Rework

# 2. (Optional) Set DLSS_SDK to package DLSS runtime libraries
$env:DLSS_SDK = "C:\path\to\DLSS_SDK"

# 3. Build the mod JAR
.\gradlew.bat build -x test -PngxShimConfig=release -PngxVendorConfig=rel
```

The resulting mod JAR will be located at `build/libs/caustica-0.1.2.jar`.

## Known limitations

The rework maintains strict transparency regarding technical boundaries, historical baselines, and scope:

- **NRD Scope (Out of Target)**: NVIDIA Real-Time Denoisers (NRD) integration remains experimental, quarantined behind `rt/reconstruction/experimental/nrd/`, and is **OUT OF TARGET / NOT REQUIRED** for production.
- **Integration Waivers (DH, Voxy, FSR 3, XeSS)**: Code integration for Distant Horizons (DH), Voxy LOD, AMD FSR 3, and Intel XeSS is present where applicable, but runtime qualification of these paths is covered under authorized project waivers and excluded from the production qualification baseline.
- **Full-Frame GPU Benchmark (Out of Scope)**: A comprehensive end-to-end full-frame GPU benchmark suite is **OUT OF SCOPE / NOT REQUIRED** for this functional baseline release.
- **Historical Canonical Baseline**: Four parallax/water failures were retained during rewrite qualification. They are now resolved as CRLF-sensitive test-harness false negatives; production shader behavior is unchanged and the current expected failure count is zero.

## Usage Notes

- Caustica is client-side only.
- DLSS Ray Reconstruction and Frame Generation require supported NVIDIA hardware and drivers.
- On Linux if Minecraft crashes on startup with stack overflow errors, try adding `-Xss2M` to the Java args to increase the stack size.
- Recommended Java args for performance:
  `-XX:+UseCompactObjectHeaders -XX:+AlwaysPreTouch -XX:+UseStringDeduplication -XX:+UseZGC`
- Frame Generation is experimental and needs to be enabled in the configuration file.
- HDR output requires an HDR swapchain and a correctly configured HDR display.
- When HDR is enabled on Linux, Caustica selects GLFW's native Wayland backend automatically. X11/XWayland surfaces generally do not expose the required HDR10/PQ format.
- If Minecraft falls back to OpenGL after a crash, re-enable the Vulkan backend before using Caustica again.

## Compatibility

Caustica takes over the world renderer, so other mods that heavily modify world rendering, shader pipelines, post-processing, or the Vulkan backend may conflict. UI-only mods are generally compatible.

## License

Caustica's project-owned source code and documentation are licensed under the GNU Lesser General Public License v3.0 or later. See [LICENSE.md](LICENSE.md), [COPYING](COPYING), and [COPYING.LESSER](COPYING.LESSER).

Release artifacts may bundle third-party SDK components under their respective license terms. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
