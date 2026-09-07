package dev.comfyfluffy.caustica.rt.device;

/**
 * Immutable read-only snapshot of the GPU/device capability state already
 * calculated by RtDeviceBringup.
 *
 * <p>AER-010 deliberately does not own Vulkan probing, extension selection,
 * feature enabling, or device bring-up. Those responsibilities remain in
 * RtDeviceBringup; this record only exposes their resulting state.</p>
 */
public record GpuCapabilities(
        boolean rtRequested,
        boolean serExtEnabled,
        boolean ommEnabled,
        boolean reflexEnabled,
        boolean presentIdEnabled,
        boolean wideLinesEnabled,
        boolean xessFeaturesEnabled,
        float maxLineWidth,
        int overlayMsaaSamples,
        int maxOpacity4StateSubdivisionLevel,
        String gpuName,
        String gpuVendorName,
        boolean looksLikeRtxFrameGenerationSeries
) {
}
