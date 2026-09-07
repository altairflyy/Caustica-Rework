# Caustica Rewrite Architecture Target

## Goal

Evolve the current renderer incrementally while preserving a known-good reference.
The migration method is:

```text
extract -> verify -> abstract -> migrate -> compare -> delete legacy
```

The target layering is:

```text
Minecraft / Fabric
        |
Scene / adapters
        |
Acceleration Structure ownership
        |
Frame Pipeline
        |
Render Graph
        |
Path Tracing
 |- ReSTIR
 |- SHaRC
 `- Volumetrics
        |
Reconstruction
 |- DLSS RR
 |- SVGF
 `- NRD experimental
        |
Upscaling
 |- DLSS
 |- FSR
 |- XeSS
 `- Native
        |
Post / HDR / Presentation
        |
Vulkan
```

## Architectural direction

- `RtComposite` is reduced progressively instead of being replaced in one rewrite.
- Frame data becomes explicit before resource ownership is moved.
- Temporal reset state becomes centralized without changing temporal algorithms.
- ReSTIR, SHaRC, and SVGF ownership is extracted before generic feature abstractions
  are introduced.
- DH/Voxy become LOD adapters behind one provider owner; the working meshing/build
  path remains the baseline during abstraction.
- A linear `FramePipeline` is made explicit before introducing a Render Graph.
- GPU resource and acceleration-structure lifetime are centralized only after the
  pipeline is understandable and verifiable.
- Reconstruction and upscaling interfaces are introduced after concrete state has
  been extracted.
- Scene ownership starts with the minimum immutable/frame contributions required for
  TLAS construction, not a speculative generic scene database.
- The Render Graph first mirrors the verified pipeline in shadow mode, then assumes
  execution and barriers one resource family at a time.
- Legacy paths and development gates are removed only after their replacements have
  become the validated default.

## Explicit non-goals

- No big-bang `engine2/` renderer.
- No generic abstraction-first redesign.
- No shader rewrite during Java ownership refactors.
- No new LOD mesher on the rewrite critical path.
- No NRD revival during reconstruction migration.
- No Render Graph before a verified explicit FramePipeline.
- No Iris dependency in the core.
- No OpenGL/DX/Vulkan HAL: Vulkan remains the backend.

## Success shape

At completion, feature parity with the frozen reference is preserved where the
reference supports it, while ownership, lifetime, reset semantics, pipeline order,
and resource access are explicit enough to test and reason about independently.
