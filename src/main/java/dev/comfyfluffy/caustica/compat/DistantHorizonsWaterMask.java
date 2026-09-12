package dev.comfyfluffy.caustica.compat;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/** Same-pass native-DH water classification attachment. No geometry is captured or redrawn here. */
public final class DistantHorizonsWaterMask {
    private static final Vector4f CLEAR = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);
    private static final ThreadLocal<Boolean> TERRAIN_PASS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> BUILDING_TERRAIN_PIPELINES =
            ThreadLocal.withInitial(() -> false);

    private static GpuTexture texture;
    private static GpuTextureView view;

    private DistantHorizonsWaterMask() {
    }

    public static void beginTerrainPipelineBuild() {
        BUILDING_TERRAIN_PIPELINES.set(true);
    }

    public static void endTerrainPipelineBuild() {
        BUILDING_TERRAIN_PIPELINES.remove();
    }

    public static void addColorTarget(RenderPipeline.Builder builder) {
        if (BUILDING_TERRAIN_PIPELINES.get()) {
            builder.withColorTargetState(1, new ColorTargetState(
                    Optional.empty(), GpuFormat.R8_UNORM, ColorTargetState.WRITE_RED));
        }
    }

    public static void beginTerrainPass(boolean opaque) {
        TERRAIN_PASS.set(opaque);
    }

    public static void endTerrainPass() {
        TERRAIN_PASS.remove();
    }

    public static RenderPass createRenderPass(CommandEncoder encoder, Supplier<String> label,
                                               GpuTextureView color, Optional<Vector4fc> colorClear,
                                               GpuTextureView depth, OptionalDouble depthClear) {
        Boolean opaque = TERRAIN_PASS.get();
        if (opaque == null) {
            return encoder.createRenderPass(label, color, colorClear, depth, depthClear);
        }
        // Consume immediately so another unrelated RenderPassWrapper cannot inherit this terrain pass.
        TERRAIN_PASS.remove();
        GpuTextureView mask = ensure(color.getWidth(0), color.getHeight(0));
        RenderPassDescriptor descriptor = RenderPassDescriptor.create(label)
                .withColorAttachment(color, colorClear)
                .withColorAttachment(mask, opaque ? Optional.of(CLEAR) : Optional.empty())
                .withDepthAttachment(depth, depthClear)
                // DH's native color target is the authoritative render extent and origin for this pass.
                .withRenderArea(new RenderPass.RenderArea(0, 0, color.getWidth(0), color.getHeight(0)));
        return encoder.createRenderPass(descriptor);
    }

    public static synchronized long imageView() {
        return view instanceof VulkanGpuTextureView vkView && !view.isClosed()
                ? vkView.vkImageView() : 0L;
    }

    public static synchronized long image() {
        return texture instanceof VulkanGpuTexture vkTexture && !texture.isClosed()
                ? vkTexture.vkImage() : 0L;
    }

    public static synchronized int width() {
        return texture != null && !texture.isClosed() ? texture.getWidth(0) : 0;
    }

    public static synchronized int height() {
        return texture != null && !texture.isClosed() ? texture.getHeight(0) : 0;
    }

    public static synchronized void destroy() {
        if (view != null) view.close();
        if (texture != null) texture.close();
        view = null;
        texture = null;
        TERRAIN_PASS.remove();
        BUILDING_TERRAIN_PIPELINES.remove();
    }

    private static synchronized GpuTextureView ensure(int width, int height) {
        if (view != null && !view.isClosed() && texture != null && !texture.isClosed()
                && texture.getWidth(0) == width && texture.getHeight(0) == height) {
            return view;
        }
        if (view != null) view.close();
        if (texture != null) texture.close();
        int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC
                | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
        texture = RenderSystem.getDevice().createTexture(
                "Caustica DH water mask", usage, GpuFormat.R8_UNORM, width, height, 1, 1);
        view = RenderSystem.getDevice().createTextureView(texture);
        return view;
    }
}
