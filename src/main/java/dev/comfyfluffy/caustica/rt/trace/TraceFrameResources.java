package dev.comfyfluffy.caustica.rt.trace;

import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.accel.RtBuffer;
import dev.comfyfluffy.caustica.rt.accel.RtImage;
import dev.comfyfluffy.caustica.rt.frame.FrameContext;
import org.lwjgl.vulkan.VK10;

import java.util.Objects;

/** Owns the complete display/render-sized working set used by tracing and reconstruction. */
public final class TraceFrameResources {
    private final long pathRecordBytes;

    private SizingKey sizingKey;
    private RtImage output;
    private RtBuffer continuationQueue;
    private RtImage gNormal;
    private RtImage gAlbedo;
    private RtImage gDepth;
    private RtImage gMotion;
    private RtImage gSpecAlbedo;
    private RtImage gSpecMotion;
    private RtImage gViewZ;
    private RtImage gNrdDiff;
    private RtImage gNrdSpec;
    private RtImage nrdDiffOut;
    private RtImage nrdSpecOut;
    private RtImage nrdCombined;
    private RtImage nrdValidation;
    private RtImage rrOutput;

    public TraceFrameResources(long pathRecordBytes) {
        if (pathRecordBytes <= 0) {
            throw new IllegalArgumentException("pathRecordBytes must be positive");
        }
        this.pathRecordBytes = pathRecordBytes;
    }

    public boolean matches(Configuration configuration) {
        return sizingKey != null && sizingKey.configuration().equals(configuration)
                && output != null && continuationQueue != null && rrOutput != null;
    }

    /** First creation phase: output and continuation queue. Callers provide the idle-safe seam. */
    public TraceFrameViews createPrimary(RtContext ctx, Configuration configuration,
                                         FrameContext.Extent renderExtent) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(renderExtent, "renderExtent");
        sizingKey = new SizingKey(configuration, renderExtent);
        int renderWidth = renderExtent.width();
        int renderHeight = renderExtent.height();
        int displayWidth = configuration.displayExtent().width();
        int displayHeight = configuration.displayExtent().height();

        output = image(ctx, renderWidth, renderHeight, VK10.VK_FORMAT_R16G16B16A16_SFLOAT, "trace color");
        long pixelRecords = Math.multiplyExact((long) renderWidth, (long) renderHeight);
        long continuationBytes = Math.multiplyExact(Math.multiplyExact(pixelRecords, 2L), pathRecordBytes);
        continuationQueue = ctx.createBuffer(continuationBytes, VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                false, "path continuation queue " + renderWidth + "x" + renderHeight + "x2");
        return views();
    }

    /** Second creation phase: all trace guide images. */
    public TraceFrameViews createGuides(RtContext ctx) {
        SizingKey key = requireSizingKey();
        int renderWidth = key.renderExtent().width();
        int renderHeight = key.renderExtent().height();
        gNormal = image(ctx, renderWidth, renderHeight, VK10.VK_FORMAT_R16G16B16A16_SFLOAT, "guide normal roughness");
        gAlbedo = image(ctx, renderWidth, renderHeight, VK10.VK_FORMAT_R16G16B16A16_SFLOAT, "guide diffuse albedo");
        gDepth = image(ctx, renderWidth, renderHeight, VK10.VK_FORMAT_R32_SFLOAT, "guide linear depth");
        gMotion = image(ctx, renderWidth, renderHeight, VK10.VK_FORMAT_R16G16_SFLOAT, "guide motion");
        gSpecAlbedo = image(ctx, renderWidth, renderHeight, VK10.VK_FORMAT_R16G16B16A16_SFLOAT, "guide specular albedo");
        gSpecMotion = image(ctx, renderWidth, renderHeight, VK10.VK_FORMAT_R16G16_SFLOAT, "guide specular motion");
        gViewZ = image(ctx, renderWidth, renderHeight, VK10.VK_FORMAT_R32_SFLOAT, "nrd viewZ");
        gNrdDiff = image(ctx, renderWidth, renderHeight, VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                "nrd diffuse radiance+hitdist");
        gNrdSpec = image(ctx, renderWidth, renderHeight, VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                "nrd specular radiance+hitdist");
        return views();
    }

    /** Final creation phase: optional NRD outputs and the shared display-resolution destination. */
    public TraceFrameViews createReconstructionOutputs(RtContext ctx) {
        SizingKey key = requireSizingKey();
        Configuration configuration = key.configuration();
        int renderWidth = key.renderExtent().width();
        int renderHeight = key.renderExtent().height();
        int displayWidth = configuration.displayExtent().width();
        int displayHeight = configuration.displayExtent().height();
        if (configuration.nrdEnabled()) {
            nrdDiffOut = image(ctx, renderWidth, renderHeight, VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                    "nrd denoised diffuse");
            nrdSpecOut = image(ctx, renderWidth, renderHeight, VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                    "nrd denoised specular");
            nrdCombined = image(ctx, renderWidth, renderHeight, VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                    "nrd combined radiance");
            nrdValidation = image(ctx, renderWidth, renderHeight, VK10.VK_FORMAT_R8G8B8A8_UNORM,
                    "nrd validation overlay");
        }
        rrOutput = image(ctx, displayWidth, displayHeight, VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                "DLSS-RR output");
        return views();
    }

    public TraceFrameViews views() {
        if (sizingKey == null) {
            return null;
        }
        return new TraceFrameViews(sizingKey, output, continuationQueue, gNormal, gAlbedo, gDepth,
                gMotion, gSpecAlbedo, gSpecMotion, gViewZ, gNrdDiff, gNrdSpec, nrdDiffOut,
                nrdSpecOut, nrdCombined, nrdValidation, rrOutput);
    }

    public void releasePrimaryAfterIdle() {
        destroy(output); output = null;
        if (continuationQueue != null) { continuationQueue.destroy(); continuationQueue = null; }
    }

    public void releaseGuidesAfterIdle() {
        destroy(gNormal); gNormal = null;
        destroy(gAlbedo); gAlbedo = null;
        destroy(gDepth); gDepth = null;
        destroy(gMotion); gMotion = null;
        destroy(gSpecAlbedo); gSpecAlbedo = null;
        destroy(gSpecMotion); gSpecMotion = null;
        destroy(gViewZ); gViewZ = null;
        destroy(gNrdDiff); gNrdDiff = null;
        destroy(gNrdSpec); gNrdSpec = null;
    }

    public void releaseReconstructionOutputsAfterIdle() {
        destroy(nrdDiffOut); nrdDiffOut = null;
        destroy(nrdSpecOut); nrdSpecOut = null;
        destroy(nrdCombined); nrdCombined = null;
        destroy(nrdValidation); nrdValidation = null;
        destroy(rrOutput); rrOutput = null;
        sizingKey = null;
    }

    public void release() {
        releasePrimaryAfterIdle();
        releaseGuidesAfterIdle();
        releaseReconstructionOutputsAfterIdle();
    }

    private SizingKey requireSizingKey() {
        if (sizingKey == null) {
            throw new IllegalStateException("primary trace resources are not allocated");
        }
        return sizingKey;
    }

    private static RtImage image(RtContext ctx, int width, int height, int format, String label) {
        return ctx.createStorageImage(width, height, format, label + " " + width + "x" + height);
    }

    private static void destroy(RtImage image) {
        if (image != null) image.destroy();
    }

    public record Configuration(FrameContext.Extent displayExtent,
                                boolean rrEnabled, int rrQuality,
                                boolean fsrEnabled, int fsrQuality,
                                boolean xessEnabled, int xessQuality,
                                boolean svgfEnabled, boolean nrdEnabled) {
        public Configuration {
            Objects.requireNonNull(displayExtent, "displayExtent");
        }
    }

    public record SizingKey(Configuration configuration, FrameContext.Extent renderExtent) {
        public SizingKey {
            Objects.requireNonNull(configuration, "configuration");
            Objects.requireNonNull(renderExtent, "renderExtent");
        }
    }

    /** Immutable borrowed snapshot. Its resources remain owned and destroyed only by this class. */
    public record TraceFrameViews(SizingKey sizingKey, RtImage output, RtBuffer continuationQueue,
                                  RtImage normal, RtImage albedo, RtImage depth, RtImage motion,
                                  RtImage specularAlbedo, RtImage specularMotion, RtImage viewZ,
                                  RtImage nrdDiffuseInput, RtImage nrdSpecularInput,
                                  RtImage nrdDiffuseOutput, RtImage nrdSpecularOutput,
                                  RtImage nrdCombined, RtImage nrdValidation, RtImage rrOutput) {
        public int displayWidth() { return sizingKey.configuration().displayExtent().width(); }
        public int displayHeight() { return sizingKey.configuration().displayExtent().height(); }
        public int renderWidth() { return sizingKey.renderExtent().width(); }
        public int renderHeight() { return sizingKey.renderExtent().height(); }
    }
}
