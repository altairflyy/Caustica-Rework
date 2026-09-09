package dev.comfyfluffy.caustica.rt.framegen;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import dev.comfyfluffy.caustica.CausticaMod;
import dev.comfyfluffy.caustica.mixin.CommandEncoderAccessor;
import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtUiOverlay;
import dev.comfyfluffy.caustica.rt.accel.RtImage;
import dev.comfyfluffy.caustica.rt.gpu.FrameTailRetirement;
import dev.comfyfluffy.caustica.rt.post.GeneratedFrameUiComposer;
import dev.comfyfluffy.caustica.rt.pipeline.RtDlssFg;
import dev.comfyfluffy.caustica.rt.pipeline.RtFgSkyMaskPipeline;
import dev.comfyfluffy.caustica.rt.pipeline.RtFgUiCompositePipeline;
import dev.comfyfluffy.caustica.rt.pipeline.RtFsrFrameGen;
import dev.comfyfluffy.caustica.rt.pipeline.RtNativeFrameGen;
import dev.comfyfluffy.caustica.rt.pipeline.RtNativeFrameGenPipeline;
import dev.comfyfluffy.caustica.rt.trace.TraceFrameResources;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageCopy;
import org.lwjgl.vulkan.VkImageMemoryBarrier2;
import org.lwjgl.vulkan.VkMemoryBarrier2;

import java.util.Objects;

/** Owns all frame-generation resources, temporal state and command recording. */
public final class FrameGenerationResources {
    private final FrameTailRetirement retirement;

    private RtImage hudlessImage;
    private RtImage hdrHudlessImage;
    private RtImage[] interp = new RtImage[0];
    private int interpW = -1;
    private int interpH = -1;
    private int interpFormat = Integer.MIN_VALUE;
    private RtImage backbufferCopy;
    private int backbufferCopyW = -1;
    private int backbufferCopyH = -1;
    private RtImage previousFrame;
    private int previousFrameW = -1;
    private int previousFrameH = -1;
    private int previousFrameFormat = Integer.MIN_VALUE;

    private RtNativeFrameGenPipeline nativePipeline;
    private RtFgUiCompositePipeline uiCompositePipeline;
    private RtFgSkyMaskPipeline skyMaskPipeline;
    private boolean nativeFailed;
    private boolean uiCompositeFailed;
    private boolean skyMaskFailed;

    private boolean reset = true;
    private boolean previousFrameValid;
    private long nativeLastUseFrame = -1;
    private boolean nativeSeededTick;
    private double previousNativeCamX;
    private double previousNativeCamY;
    private double previousNativeCamZ;
    private boolean nativeCameraValid;
    private float jitterX;
    private float jitterY;
    private final Matrix4f clipToPrevious = new Matrix4f();
    private final Matrix4f previousToClip = new Matrix4f();
    private final Matrix4f matrixScratch = new Matrix4f();
    private final float[] cameraPosition = new float[3];
    private final float[] cameraRight = new float[3];
    private final float[] cameraUp = new float[3];
    private final float[] cameraForward = new float[3];

    public FrameGenerationResources(FrameTailRetirement retirement) {
        this.retirement = Objects.requireNonNull(retirement, "retirement");
    }

    public static int generatedCount() {
        if (RtNativeFrameGen.enabled()) return RtNativeFrameGen.INSTANCE.effectiveGeneratedCount();
        if (RtFsrFrameGen.enabled()) return RtFsrFrameGen.INSTANCE.effectiveGeneratedCount();
        return RtDlssFg.INSTANCE.effectiveMultiFrameCount();
    }

    public static boolean hudlessNeeded() {
        return RtDlssFg.enabled() || RtNativeFrameGen.enabled();
    }

    public void captureJitter(float x, float y) {
        jitterX = x;
        jitterY = y;
    }

    public boolean resetPending() { return reset; }
    public boolean historyValid() { return previousFrameValid; }

    public void invalidate() {
        reset = true;
        previousFrameValid = false;
        nativeCameraValid = false;
        nativeLastUseFrame = -1;
    }

    public void captureHudless(RenderTarget main) {
        if (!hudlessNeeded() || !RtUiOverlay.enabled() || main == null || main.getColorTexture() == null) return;
        RtContext ctx = RtContext.currentOrNull();
        if (ctx == null) return;
        long srcImage;
        try {
            if (!(main.getColorTexture() instanceof com.mojang.blaze3d.vulkan.VulkanGpuTexture texture)) return;
            srcImage = texture.vkImage();
        } catch (IllegalStateException e) {
            return;
        }
        if (hudlessImage == null || hudlessImage.width != main.width || hudlessImage.height != main.height) {
            retire(hudlessImage);
            hudlessImage = ctx.createStorageImage(main.width, main.height, VK10.VK_FORMAT_R8G8B8A8_UNORM,
                    "FG hudless capture " + main.width + "x" + main.height);
        }
        var encoder = (VulkanCommandEncoder) ((CommandEncoderAccessor) RenderSystem.getDevice()
                .createCommandEncoder()).caustica$getBackend();
        VkCommandBuffer command = encoder.allocateAndBeginTransientCommandBuffer();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VulkanCommandEncoder.memoryBarrier(command, stack);
            VK10.vkCmdCopyImage(command, srcImage, VK10.VK_IMAGE_LAYOUT_GENERAL,
                    hudlessImage.image, VK10.VK_IMAGE_LAYOUT_GENERAL, copyRegion(stack, main.width, main.height));
            VulkanCommandEncoder.memoryBarrier(command, stack);
        }
        if (VK10.vkEndCommandBuffer(command) != VK10.VK_SUCCESS) {
            throw new IllegalStateException("vkEndCommandBuffer(fg hudless capture) failed");
        }
        encoder.execute(command);
    }

    public void captureHdrHudless(RtContext ctx, VkCommandBuffer command, MemoryStack stack, RtImage source) {
        if (hdrHudlessImage == null || hdrHudlessImage.width != source.width || hdrHudlessImage.height != source.height) {
            retire(hdrHudlessImage);
            hdrHudlessImage = ctx.createStorageImage(source.width, source.height,
                    VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                    "FG HDR hudless capture (PQ) " + source.width + "x" + source.height);
        }
        VulkanCommandEncoder.memoryBarrier(command, stack);
        VK10.vkCmdCopyImage(command, source.image, VK10.VK_IMAGE_LAYOUT_GENERAL,
                hdrHudlessImage.image, VK10.VK_IMAGE_LAYOUT_GENERAL,
                copyRegion(stack, source.width, source.height));
        VulkanCommandEncoder.memoryBarrier(command, stack);
    }

    public RtImage interpolate(VulkanCommandEncoder encoder, long backbufferView, long backbufferImage,
                               int swapW, int swapH, int index, int count, boolean hdrBackbuffer,
                               FrameData frame, GeneratedFrameUiComposer uiComposer) {
        TraceFrameResources.TraceFrameViews views = frame.traceViews();
        if (!frame.rendererReady() || views == null || views.depth() == null || views.motion() == null) return null;
        RtContext ctx = RtContext.currentOrNull();
        if (ctx == null) return null;
        int format = hdrBackbuffer ? VK10.VK_FORMAT_R16G16B16A16_SFLOAT : VK10.VK_FORMAT_R8G8B8A8_UNORM;
        if (RtNativeFrameGen.enabled()) {
            return interpolateNative(ctx, encoder, backbufferView, backbufferImage, swapW, swapH,
                    index, count, hdrBackbuffer, format, frame, uiComposer);
        }
        if (RtFsrFrameGen.enabled()) {
            return interpolateFsr(ctx, encoder, backbufferView, backbufferImage, swapW, swapH,
                    index, count, hdrBackbuffer, format, frame);
        }
        if (index == 1) {
            if (!ensureDlssFeature(ctx, swapW, swapH, views.renderWidth(), views.renderHeight(), format)) {
                throw new IllegalStateException("DLSSG feature not ready (ensureFgFeature failed)");
            }
            ensureInterp(ctx, count, swapW, swapH, format);
            matrixScratch.set(frame.currentProjectionView()).invert();
            clipToPrevious.set(frame.previousProjectionView()).mul(matrixScratch);
            matrixScratch.set(frame.previousProjectionView()).invert();
            previousToClip.set(frame.currentProjectionView()).mul(matrixScratch);
        }
        RtImage output = interpAt(index, "fgInterpolate");
        RtImage hudless = hdrBackbuffer ? hdrHudlessImage : hudlessImage;
        boolean hudlessReady = hudless != null && hudless.width == swapW && hudless.height == swapH;
        boolean uiReady = RtUiOverlay.overlayWidth() == swapW && RtUiOverlay.overlayHeight() == swapH
                && RtUiOverlay.overlayColorView() != 0L && RtUiOverlay.overlayColorImage() != 0L;
        VkCommandBuffer command = encoder.allocateAndBeginTransientCommandBuffer();
        boolean ok = RtDlssFg.INSTANCE.evaluate(command.address(), backbufferView, backbufferImage, format,
                views.depth().view, views.depth().image, VK10.VK_FORMAT_R32_SFLOAT,
                views.motion().view, views.motion().image, VK10.VK_FORMAT_R16G16_SFLOAT,
                hudlessReady ? hudless.view : 0L, hudlessReady ? hudless.image : 0L,
                hudlessReady ? format : 0,
                uiReady ? RtUiOverlay.overlayColorView() : 0L,
                uiReady ? RtUiOverlay.overlayColorImage() : 0L,
                uiReady ? VK10.VK_FORMAT_R8G8B8A8_UNORM : 0,
                output.view, output.image, format, swapW, swapH, views.renderWidth(), views.renderHeight(),
                count, index, 1.0f, 1.0f, true, hdrBackbuffer, true, reset,
                clipToPrevious, previousToClip);
        if (VK10.vkEndCommandBuffer(command) != VK10.VK_SUCCESS) {
            throw new IllegalStateException("vkEndCommandBuffer(fg interpolate) failed");
        }
        reset = false;
        if (!ok) throw new IllegalStateException("ngxshim_evaluate_dlssg failed (RtDlssFg.evaluate returned false)");
        encoder.execute(command);
        return output;
    }

    private RtImage interpolateFsr(RtContext ctx, VulkanCommandEncoder encoder, long backbufferView,
                                   long backbufferImage, int swapW, int swapH, int index, int count,
                                   boolean hdr, int format, FrameData frame) {
        TraceFrameResources.TraceFrameViews views = frame.traceViews();
        if (index == 1) {
            if (!RtFsrFrameGen.INSTANCE.ensureFeature(swapW, swapH, views.renderWidth(), views.renderHeight(), format)) {
                throw new IllegalStateException("FSR FG feature not ready (ensureFeature failed)");
            }
            ensureInterp(ctx, count, swapW, swapH, format);
            long[] outputs = new long[count];
            for (int i = 0; i < count; i++) outputs[i] = interp[i].image;
            setVec3(cameraPosition, frame.cameraX(), frame.cameraY(), frame.cameraZ());
            setVec3(cameraRight, frame.viewRotation().m00(), frame.viewRotation().m01(), frame.viewRotation().m02());
            setVec3(cameraUp, frame.viewRotation().m10(), frame.viewRotation().m11(), frame.viewRotation().m12());
            setVec3(cameraForward, frame.viewRotation().m20(), frame.viewRotation().m21(), frame.viewRotation().m22());
            long presentImage = backbufferImage;
            int presentFormat = format;
            if (!hdr) {
                recordBackbufferCopy(ctx, encoder, backbufferImage, swapW, swapH);
                presentImage = backbufferCopy.image;
                presentFormat = VK10.VK_FORMAT_R8G8B8A8_UNORM;
            }
            VkCommandBuffer command = encoder.allocateAndBeginTransientCommandBuffer();
            float fovY = (float) (2.0 * Math.atan(1.0 / Math.abs(frame.projection().m11())));
            boolean ok = RtFsrFrameGen.INSTANCE.prepareAndGenerate(command.address(),
                    views.depth().image, views.motion().image, views.renderWidth(), views.renderHeight(),
                    -jitterX, -jitterY, fovY, cameraPosition, cameraUp, cameraRight, cameraForward,
                    presentImage, presentFormat, outputs, count, swapW, swapH, hdr);
            ensureSkyMask(ctx);
            if (ok && skyMaskPipeline != null) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    VulkanCommandEncoder.memoryBarrier(command, stack);
                    long realView = hdr ? backbufferView : backbufferCopy.view;
                    for (RtImage image : interp) {
                        skyMaskPipeline.dispatch(command, image.view, realView, views.depth().view,
                                swapW, swapH, views.renderWidth(), views.renderHeight(), hdr);
                    }
                }
            }
            if (VK10.vkEndCommandBuffer(command) != VK10.VK_SUCCESS) {
                throw new IllegalStateException("vkEndCommandBuffer(fsr fg) failed");
            }
            if (!ok) throw new IllegalStateException("fsrshim FG prepare/generate failed");
            encoder.execute(command);
        }
        return interpAt(index, "FSR fgInterpolate");
    }

    private RtImage interpolateNative(RtContext ctx, VulkanCommandEncoder encoder, long backbufferView,
                                      long backbufferImage, int swapW, int swapH, int index, int count,
                                      boolean hdr, int format, FrameData frame,
                                      GeneratedFrameUiComposer uiComposer) {
        TraceFrameResources.TraceFrameViews views = frame.traceViews();
        if (index == 1) {
            nativeSeededTick = false;
            ensureNativePipeline(ctx);
            if (nativePipeline == null) throw new IllegalStateException("native FG pipeline not ready");
            ensureInterp(ctx, count, swapW, swapH, format);
            RtImage hudless = hdr ? hdrHudlessImage : hudlessImage;
            boolean hudlessReady = hudless != null && hudless.width == swapW && hudless.height == swapH;
            long currentView;
            long currentImage;
            if (hudlessReady) {
                currentView = hudless.view;
                currentImage = hudless.image;
            } else if (!hdr) {
                recordBackbufferCopy(ctx, encoder, backbufferImage, swapW, swapH);
                currentView = backbufferCopy.view;
                currentImage = backbufferCopy.image;
            } else {
                currentView = backbufferView;
                currentImage = backbufferImage;
            }
            ensurePreviousFrame(ctx, swapW, swapH, format);
            if (nativeLastUseFrame >= 0 && frame.frameIndex() - nativeLastUseFrame > 2) previousFrameValid = false;
            nativeLastUseFrame = frame.frameIndex();
            if (nativeCameraValid) {
                double dx = frame.cameraX() - previousNativeCamX;
                double dy = frame.cameraY() - previousNativeCamY;
                double dz = frame.cameraZ() - previousNativeCamZ;
                if (dx * dx + dy * dy + dz * dz > 32.0 * 32.0) previousFrameValid = false;
            }
            previousNativeCamX = frame.cameraX();
            previousNativeCamY = frame.cameraY();
            previousNativeCamZ = frame.cameraZ();
            nativeCameraValid = true;
            boolean hadPrevious = previousFrameValid;
            VkCommandBuffer command = encoder.allocateAndBeginTransientCommandBuffer();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                if (hadPrevious) {
                    VulkanCommandEncoder.memoryBarrier(command, stack);
                    for (int k = 0; k < count; k++) {
                        nativePipeline.dispatch(command, interp[k].view, currentView, previousFrame.view,
                                views.motion().view, swapW, swapH, views.renderWidth(), views.renderHeight(),
                                (k + 1.0f) / (count + 1.0f), hdr);
                    }
                    ensureSkyMask(ctx);
                    if (skyMaskPipeline != null) {
                        VulkanCommandEncoder.memoryBarrier(command, stack);
                        for (RtImage image : interp) {
                            skyMaskPipeline.dispatch(command, image.view, currentView, views.depth().view,
                                    swapW, swapH, views.renderWidth(), views.renderHeight(), hdr);
                        }
                    }
                    long overlayView = hudlessReady ? RtUiOverlay.overlayColorView() : 0L;
                    boolean uiReady = overlayView != 0L && RtUiOverlay.overlayWidth() == swapW
                            && RtUiOverlay.overlayHeight() == swapH;
                    if (uiReady) {
                        long sampler = uiComposer.uiSampler(ctx);
                        if (sampler != 0L) {
                            VulkanCommandEncoder.memoryBarrier(command, stack);
                            if (hdr) {
                                for (RtImage image : interp) {
                                    uiComposer.composeHdrGenerated(command, image, overlayView, swapW, swapH);
                                }
                            } else {
                                ensureUiComposite(ctx);
                                if (uiCompositePipeline != null) {
                                    for (RtImage image : interp) {
                                        uiCompositePipeline.dispatch(command, image.view, overlayView,
                                                sampler, swapW, swapH);
                                    }
                                }
                            }
                        }
                    }
                    VulkanCommandEncoder.memoryBarrier(command, stack);
                }
                VK10.vkCmdCopyImage(command, currentImage, VK10.VK_IMAGE_LAYOUT_GENERAL,
                        previousFrame.image, VK10.VK_IMAGE_LAYOUT_GENERAL, copyRegion(stack, swapW, swapH));
                VulkanCommandEncoder.memoryBarrier(command, stack);
            }
            if (VK10.vkEndCommandBuffer(command) != VK10.VK_SUCCESS) {
                throw new IllegalStateException("vkEndCommandBuffer(native fg) failed");
            }
            encoder.execute(command);
            previousFrameValid = true;
            if (!hadPrevious) {
                nativeSeededTick = true;
                return null;
            }
        }
        if (nativeSeededTick) return null;
        return interpAt(index, "native fgInterpolate");
    }

    private boolean ensureDlssFeature(RtContext ctx, int w, int h, int rw, int rh, int format) {
        if (RtDlssFg.INSTANCE.featureReadyFor(w, h, rw, rh, format)) return true;
        ctx.submitSync(command -> RtDlssFg.INSTANCE.ensureFeature(command.address(), w, h, rw, rh, format));
        reset = true;
        return RtDlssFg.INSTANCE.featureReadyFor(w, h, rw, rh, format);
    }

    private void ensureInterp(RtContext ctx, int count, int w, int h, int format) {
        if (interp.length == count && interpW == w && interpH == h && interpFormat == format
                && (count == 0 || interp[0] != null)) return;
        RtImage[] old = interp;
        interp = new RtImage[count];
        for (int i = 0; i < count; i++) {
            interp[i] = ctx.createStorageImage(w, h, format, "FG interp " + i + " " + w + "x" + h);
        }
        interpW = w;
        interpH = h;
        interpFormat = format;
        for (RtImage image : old) retire(image);
    }

    private void ensureBackbufferCopy(RtContext ctx, int w, int h) {
        if (backbufferCopy != null && backbufferCopyW == w && backbufferCopyH == h) return;
        RtImage old = backbufferCopy;
        backbufferCopy = ctx.createStorageImage(w, h, VK10.VK_FORMAT_R8G8B8A8_UNORM,
                "FG backbuffer copy " + w + "x" + h);
        backbufferCopyW = w;
        backbufferCopyH = h;
        retire(old);
    }

    private void recordBackbufferCopy(RtContext ctx, VulkanCommandEncoder encoder, long source,
                                      int width, int height) {
        ensureBackbufferCopy(ctx, width, height);
        VkCommandBuffer command = encoder.allocateAndBeginTransientCommandBuffer();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier2.Buffer toDestination = VkImageMemoryBarrier2.calloc(1, stack).sType$Default();
            toDestination.get(0).srcStageMask(0L).srcAccessMask(0L).dstStageMask(4096L).dstAccessMask(4096L)
                    .oldLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED).newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .srcQueueFamilyIndex(-1).dstQueueFamilyIndex(-1).image(backbufferCopy.image);
            toDestination.get(0).subresourceRange().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
            VkMemoryBarrier2.Buffer visibility = VkMemoryBarrier2.calloc(1, stack).sType$Default();
            visibility.get(0).srcStageMask(1024L | 4096L).srcAccessMask(256L | 8L)
                    .dstStageMask(4096L).dstAccessMask(8L);
            KHRSynchronization2.vkCmdPipelineBarrier2KHR(command,
                    VkDependencyInfo.calloc(stack).sType$Default()
                            .pImageMemoryBarriers(toDestination).pMemoryBarriers(visibility));
            VkImageBlit.Buffer region = VkImageBlit.calloc(1, stack);
            region.get(0).srcSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0).baseArrayLayer(0).layerCount(1);
            region.get(0).dstSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0).baseArrayLayer(0).layerCount(1);
            region.get(0).srcOffsets(1).set(width, height, 1);
            region.get(0).dstOffsets(1).set(width, height, 1);
            VK10.vkCmdBlitImage(command, source, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    backbufferCopy.image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region, VK10.VK_FILTER_NEAREST);
            VkImageMemoryBarrier2.Buffer toGeneral = VkImageMemoryBarrier2.calloc(1, stack).sType$Default();
            toGeneral.get(0).srcStageMask(4096L).srcAccessMask(4096L).dstStageMask(65536L).dstAccessMask(98304L)
                    .oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL).newLayout(VK10.VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(-1).dstQueueFamilyIndex(-1).image(backbufferCopy.image);
            toGeneral.get(0).subresourceRange().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
            KHRSynchronization2.vkCmdPipelineBarrier2KHR(command,
                    VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(toGeneral));
        }
        if (VK10.vkEndCommandBuffer(command) != VK10.VK_SUCCESS) {
            throw new IllegalStateException("vkEndCommandBuffer(fg backbuffer copy) failed");
        }
        encoder.execute(command);
    }

    private void ensurePreviousFrame(RtContext ctx, int w, int h, int format) {
        if (previousFrame != null && previousFrameW == w && previousFrameH == h
                && previousFrameFormat == format) return;
        RtImage old = previousFrame;
        previousFrame = ctx.createStorageImage(w, h, format, "native FG prev frame " + w + "x" + h);
        previousFrameW = w;
        previousFrameH = h;
        previousFrameFormat = format;
        previousFrameValid = false;
        retire(old);
    }

    private void ensureNativePipeline(RtContext ctx) {
        if (nativePipeline != null || nativeFailed) return;
        try {
            nativePipeline = RtNativeFrameGenPipeline.create(ctx);
            CausticaMod.LOGGER.info("Caustica native frame generation active (motion-vector interpolation)");
        } catch (Throwable failure) {
            nativeFailed = true;
            CausticaMod.LOGGER.error("native FG pipeline creation failed; frame generation disabled", failure);
        }
    }

    private void ensureUiComposite(RtContext ctx) {
        if (uiCompositePipeline != null || uiCompositeFailed) return;
        try {
            uiCompositePipeline = RtFgUiCompositePipeline.create(ctx);
        } catch (Throwable failure) {
            uiCompositeFailed = true;
            CausticaMod.LOGGER.error("FG UI composite pipeline creation failed; generated frames stay HUD-less", failure);
        }
    }

    private void ensureSkyMask(RtContext ctx) {
        if (skyMaskPipeline != null || skyMaskFailed) return;
        try {
            skyMaskPipeline = RtFgSkyMaskPipeline.create(ctx);
            CausticaMod.LOGGER.info("FG sky mask active (generated frames copy the real frame's sky)");
        } catch (Throwable failure) {
            skyMaskFailed = true;
            CausticaMod.LOGGER.error("FG sky mask pipeline creation failed; generated frames keep raw FG sky", failure);
        }
    }

    private RtImage interpAt(int index, String operation) {
        if (index < 1 || index > interp.length || interp[index - 1] == null) {
            throw new IllegalStateException(operation + " index " + index + " out of range for fgInterp[" + interp.length + "]");
        }
        return interp[index - 1];
    }

    private void retire(RtImage image) {
        if (image != null) retirement.retire(image::destroy);
    }

    /** Device-idle teardown; no deferred callback may outlive the owner/context. */
    public void destroyAfterDeviceIdle() {
        if (skyMaskPipeline != null) skyMaskPipeline.destroy();
        if (uiCompositePipeline != null) uiCompositePipeline.destroy();
        if (nativePipeline != null) nativePipeline.destroy();
        if (hudlessImage != null) hudlessImage.destroy();
        if (hdrHudlessImage != null) hdrHudlessImage.destroy();
        for (RtImage image : interp) if (image != null) image.destroy();
        if (backbufferCopy != null) backbufferCopy.destroy();
        if (previousFrame != null) previousFrame.destroy();
        hudlessImage = null;
        hdrHudlessImage = null;
        interp = new RtImage[0];
        backbufferCopy = null;
        previousFrame = null;
        skyMaskPipeline = null;
        uiCompositePipeline = null;
        nativePipeline = null;
        invalidate();
        nativeSeededTick = false;
    }

    private static void setVec3(float[] target, double x, double y, double z) {
        target[0] = (float) x;
        target[1] = (float) y;
        target[2] = (float) z;
    }

    private static VkImageCopy.Buffer copyRegion(MemoryStack stack, int width, int height) {
        VkImageCopy.Buffer region = VkImageCopy.calloc(1, stack);
        region.get(0).srcSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .mipLevel(0).baseArrayLayer(0).layerCount(1);
        region.get(0).dstSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .mipLevel(0).baseArrayLayer(0).layerCount(1);
        region.get(0).extent().set(width, height, 1);
        return region;
    }

    public record FrameData(TraceFrameResources.TraceFrameViews traceViews,
                            Matrix4fc currentProjectionView, Matrix4fc previousProjectionView,
                            Matrix4fc projection, Matrix4fc viewRotation,
                            double cameraX, double cameraY, double cameraZ,
                            long frameIndex, boolean rendererReady) {
        public FrameData {
            Objects.requireNonNull(currentProjectionView, "currentProjectionView");
            Objects.requireNonNull(previousProjectionView, "previousProjectionView");
            Objects.requireNonNull(projection, "projection");
            Objects.requireNonNull(viewRotation, "viewRotation");
        }
    }
}
