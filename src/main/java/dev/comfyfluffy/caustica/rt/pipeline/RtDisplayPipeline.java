package dev.comfyfluffy.caustica.rt.pipeline;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.joml.Matrix4f;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtDebugLabels;

import static dev.comfyfluffy.caustica.rt.RtContext.check;

/** Compute pass that maps the display-res HDR RT image into an LDR image compatible with the main target. */
public final class RtDisplayPipeline {
    private static final String SHADER_DIR = "/caustica/rt/";
    /**
     * Push constants: int hdrEnabled, float paperWhiteNits/headroom, int tonemapOperator,
     * float tonemapExposureEv/gamma/saturation/contrast, int hybridEnabled, int renderWidth,
     * int renderHeight, float nativeDepthClear, mat4 DH inverse view-projection,
     * vec4 DH light direction/direct-strength, int DH water-mask debug flag.
     */
    private static final int PUSH_BYTES = 33 * Integer.BYTES;

    private final RtContext ctx;
    private final long descriptorSetLayout;
    private final long descriptorPool;
    private final long descriptorSet;
    private final long pipelineLayout;
    private final long pipeline;
    private final long backgroundSampler;
    private long boundOutputView;
    private long boundRtView;
    private long boundExposureView;
    private long boundHdrView;
    private long boundViewZView;
    private long boundBackgroundView;
    private long boundBackgroundDepthView;
    private long boundWaterMaskView;
    private long boundReflectionView;
    private int boundBackgroundLayout;
    private int boundWaterMaskLayout;
    private int boundReflectionLayout;
    private boolean destroyed;

    private RtDisplayPipeline(RtContext ctx, long dsl, long pool, long set, long layout, long pipeline,
                              long backgroundSampler) {
        this.ctx = ctx;
        this.descriptorSetLayout = dsl;
        this.descriptorPool = pool;
        this.descriptorSet = set;
        this.pipelineLayout = layout;
        this.pipeline = pipeline;
        this.backgroundSampler = backgroundSampler;
    }

    public static RtDisplayPipeline create(RtContext ctx) {
        VkDevice vk = ctx.vk();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer binds = VkDescriptorSetLayoutBinding.calloc(9, stack);
            binds.get(0).binding(0).descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
            binds.get(1).binding(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
            binds.get(2).binding(2).descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
            binds.get(3).binding(3).descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
            binds.get(4).binding(4).descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
            binds.get(5).binding(5).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
            binds.get(6).binding(6).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
            binds.get(7).binding(7).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
            binds.get(8).binding(8).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);

            VkDescriptorSetLayoutCreateInfo dslci = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default().pBindings(binds);
            LongBuffer p = stack.mallocLong(1);
            check(VK10.vkCreateDescriptorSetLayout(vk, dslci, null, p), "vkCreateDescriptorSetLayout(rt display)");
            long dsl = p.get(0);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_DESCRIPTOR_SET_LAYOUT, dsl, "display descriptor set layout");

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(2, stack);
            poolSizes.get(0).type(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(5);
            poolSizes.get(1).type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(4);

            VkDescriptorPoolCreateInfo dpci = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                    .maxSets(1).pPoolSizes(poolSizes);
            check(VK10.vkCreateDescriptorPool(vk, dpci, null, p), "vkCreateDescriptorPool(rt display)");
            long pool = p.get(0);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_DESCRIPTOR_POOL, pool, "display descriptor pool");

            VkDescriptorSetAllocateInfo dsai = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                    .descriptorPool(pool).pSetLayouts(stack.longs(dsl));
            LongBuffer pSet = stack.mallocLong(1);
            check(VK10.vkAllocateDescriptorSets(vk, dsai, pSet), "vkAllocateDescriptorSets(rt display)");
            long set = pSet.get(0);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_DESCRIPTOR_SET, set, "display descriptor set");

            VkPushConstantRange.Buffer pushRange = VkPushConstantRange.calloc(1, stack);
            pushRange.get(0).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(PUSH_BYTES);
            VkPipelineLayoutCreateInfo plci = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                    .pSetLayouts(stack.longs(dsl)).pPushConstantRanges(pushRange);
            check(VK10.vkCreatePipelineLayout(vk, plci, null, p), "vkCreatePipelineLayout(rt display)");
            long layout = p.get(0);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_PIPELINE_LAYOUT, layout, "display pipeline layout");

            long module = loadModule(vk, stack, "display.comp.spv");
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_SHADER_MODULE, module, "display shader module");
            VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack).sType$Default()
                    .stage(VK10.VK_SHADER_STAGE_COMPUTE_BIT).module(module).pName(stack.UTF8("main"));
            VkComputePipelineCreateInfo.Buffer cpci = VkComputePipelineCreateInfo.calloc(1, stack);
            cpci.get(0).sType$Default().stage(stage).layout(layout);
            LongBuffer pPipeline = stack.mallocLong(1);
            check(VK10.vkCreateComputePipelines(vk, VK10.VK_NULL_HANDLE, cpci, null, pPipeline),
                    "vkCreateComputePipelines(rt display)");
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_PIPELINE, pPipeline.get(0), "display compute pipeline");
            VK10.vkDestroyShaderModule(vk, module, null);

            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack).sType$Default()
                    .magFilter(VK10.VK_FILTER_LINEAR).minFilter(VK10.VK_FILTER_LINEAR)
                    .mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .maxLod(0.0f);
            LongBuffer pSampler = stack.mallocLong(1);
            check(VK10.vkCreateSampler(vk, samplerInfo, null, pSampler), "vkCreateSampler(rt display background)");
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_SAMPLER, pSampler.get(0), "display background sampler");

            return new RtDisplayPipeline(ctx, dsl, pool, set, layout, pPipeline.get(0), pSampler.get(0));
        }
    }

    public void setImages(long outputImageView, long rtImageView, long exposureImageView, long hdrImageView,
                          long viewZImageView, long backgroundImageView, long backgroundDepthImageView,
                          long waterMaskImageView, int backgroundImageLayout, int waterMaskImageLayout) {
        setImages(outputImageView, rtImageView, exposureImageView, hdrImageView, viewZImageView,
                backgroundImageView, backgroundDepthImageView, waterMaskImageView, backgroundImageView,
                backgroundImageLayout, waterMaskImageLayout, backgroundImageLayout);
    }

    public void setImages(long outputImageView, long rtImageView, long exposureImageView, long hdrImageView,
                          long viewZImageView, long backgroundImageView, long backgroundDepthImageView,
                          long waterMaskImageView, long reflectionImageView,
                          int backgroundImageLayout, int waterMaskImageLayout, int reflectionImageLayout) {
        if (boundOutputView == outputImageView && boundRtView == rtImageView
                && boundExposureView == exposureImageView && boundHdrView == hdrImageView
                && boundViewZView == viewZImageView && boundBackgroundView == backgroundImageView
                && boundBackgroundDepthView == backgroundDepthImageView
                && boundWaterMaskView == waterMaskImageView
                && boundReflectionView == reflectionImageView
                && boundBackgroundLayout == backgroundImageLayout
                && boundWaterMaskLayout == waterMaskImageLayout
                && boundReflectionLayout == reflectionImageLayout) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorImageInfo.Buffer outputInfo = VkDescriptorImageInfo.calloc(1, stack);
            outputInfo.get(0).imageView(outputImageView).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
            VkDescriptorImageInfo.Buffer rtInfo = VkDescriptorImageInfo.calloc(1, stack);
            rtInfo.get(0).imageView(rtImageView).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
            VkDescriptorImageInfo.Buffer exposureInfo = VkDescriptorImageInfo.calloc(1, stack);
            exposureInfo.get(0).imageView(exposureImageView).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
            VkDescriptorImageInfo.Buffer hdrInfo = VkDescriptorImageInfo.calloc(1, stack);
            hdrInfo.get(0).imageView(hdrImageView).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
            VkDescriptorImageInfo.Buffer viewZInfo = VkDescriptorImageInfo.calloc(1, stack);
            viewZInfo.get(0).imageView(viewZImageView).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
            VkDescriptorImageInfo.Buffer backgroundInfo = VkDescriptorImageInfo.calloc(1, stack);
            backgroundInfo.get(0).sampler(backgroundSampler).imageView(backgroundImageView)
                    .imageLayout(backgroundImageLayout);
            VkDescriptorImageInfo.Buffer backgroundDepthInfo = VkDescriptorImageInfo.calloc(1, stack);
            backgroundDepthInfo.get(0).sampler(backgroundSampler).imageView(backgroundDepthImageView)
                    .imageLayout(backgroundImageLayout);
            VkDescriptorImageInfo.Buffer waterMaskInfo = VkDescriptorImageInfo.calloc(1, stack);
            waterMaskInfo.get(0).sampler(backgroundSampler).imageView(waterMaskImageView)
                    .imageLayout(waterMaskImageLayout);
            VkDescriptorImageInfo.Buffer reflectionInfo = VkDescriptorImageInfo.calloc(1, stack);
            reflectionInfo.get(0).sampler(backgroundSampler).imageView(reflectionImageView)
                    .imageLayout(reflectionImageLayout);

            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(9, stack);
            writes.get(0).sType$Default().dstSet(descriptorSet).dstBinding(0)
                    .descriptorCount(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).pImageInfo(outputInfo);
            writes.get(1).sType$Default().dstSet(descriptorSet).dstBinding(1)
                    .descriptorCount(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).pImageInfo(rtInfo);
            writes.get(2).sType$Default().dstSet(descriptorSet).dstBinding(2)
                    .descriptorCount(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).pImageInfo(exposureInfo);
            writes.get(3).sType$Default().dstSet(descriptorSet).dstBinding(3)
                    .descriptorCount(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).pImageInfo(hdrInfo);
            writes.get(4).sType$Default().dstSet(descriptorSet).dstBinding(4)
                    .descriptorCount(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).pImageInfo(viewZInfo);
            writes.get(5).sType$Default().dstSet(descriptorSet).dstBinding(5)
                    .descriptorCount(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).pImageInfo(backgroundInfo);
            writes.get(6).sType$Default().dstSet(descriptorSet).dstBinding(6)
                    .descriptorCount(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).pImageInfo(backgroundDepthInfo);
            writes.get(7).sType$Default().dstSet(descriptorSet).dstBinding(7)
                    .descriptorCount(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).pImageInfo(waterMaskInfo);
            writes.get(8).sType$Default().dstSet(descriptorSet).dstBinding(8)
                    .descriptorCount(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).pImageInfo(reflectionInfo);
            VK10.vkUpdateDescriptorSets(ctx.vk(), writes, null);
        }
        boundOutputView = outputImageView;
        boundRtView = rtImageView;
        boundExposureView = exposureImageView;
        boundHdrView = hdrImageView;
        boundViewZView = viewZImageView;
        boundBackgroundView = backgroundImageView;
        boundBackgroundDepthView = backgroundDepthImageView;
        boundWaterMaskView = waterMaskImageView;
        boundReflectionView = reflectionImageView;
        boundBackgroundLayout = backgroundImageLayout;
        boundWaterMaskLayout = waterMaskImageLayout;
        boundReflectionLayout = reflectionImageLayout;
    }

    /**
     * Run the display mapping. The SDR tonemapped output is always written (binding 0). When
     * {@code hdrEnabled}, the PQ-encoded HDR image (binding 3) is also written using the
     * paper-white/headroom mapping.
     */
    public void dispatch(VkCommandBuffer cmd, int width, int height, boolean hdrEnabled, float paperWhiteNits,
                         float headroom, int tonemapOperator, float tonemapExposureEv, float tonemapGamma,
                          float tonemapSaturation, float tonemapContrast, int renderWidth, int renderHeight,
                          boolean hybridEnabled, float nativeDepthClear,
                          boolean dhFarLighting, Matrix4f dhInverseViewProjection,
                          float dhLightX, float dhLightY, float dhLightZ, float dhDirectStrength,
                          boolean dhWaterMaskDebug) {
        try (MemoryStack stack = MemoryStack.stackPush(); RtDebugLabels.Scope ignored = RtDebugLabels.scope(ctx, cmd, "display compute")) {
            VK10.vkCmdBindPipeline(cmd, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            VK10.vkCmdBindDescriptorSets(cmd, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipelineLayout, 0, stack.longs(descriptorSet), null);
            ByteBuffer push = stack.malloc(PUSH_BYTES);
            push.putInt(0, hdrEnabled ? 1 : 0);
            push.putFloat(4, paperWhiteNits);
            push.putFloat(8, headroom);
            push.putInt(12, tonemapOperator);
            push.putFloat(16, tonemapExposureEv);
            push.putFloat(20, tonemapGamma);
            push.putFloat(24, tonemapSaturation);
            push.putFloat(28, tonemapContrast);
            push.putInt(32, hybridEnabled ? 1 : 0);
            push.putInt(36, renderWidth);
            push.putInt(40, renderHeight);
            push.putFloat(44, nativeDepthClear);
            dhInverseViewProjection.get(48, push);
            push.putFloat(112, dhLightX);
            push.putFloat(116, dhLightY);
            push.putFloat(120, dhLightZ);
            push.putFloat(124, dhFarLighting ? dhDirectStrength : 0.0f);
            push.putInt(128, dhWaterMaskDebug ? 1 : 0);
            VK10.vkCmdPushConstants(cmd, pipelineLayout, VK10.VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
            VK10.vkCmdDispatch(cmd, (width + 15) / 16, (height + 15) / 16, 1);
        }
    }

    public void destroy() {
        if (destroyed) {
            return;
        }
        VkDevice vk = ctx.vk();
        VK10.vkDestroyPipeline(vk, pipeline, null);
        VK10.vkDestroyPipelineLayout(vk, pipelineLayout, null);
        VK10.vkDestroySampler(vk, backgroundSampler, null);
        VK10.vkDestroyDescriptorPool(vk, descriptorPool, null);
        VK10.vkDestroyDescriptorSetLayout(vk, descriptorSetLayout, null);
        destroyed = true;
    }

    private static long loadModule(VkDevice vk, MemoryStack stack, String name) {
        byte[] bytes;
        try (InputStream in = RtDisplayPipeline.class.getResourceAsStream(SHADER_DIR + name)) {
            if (in == null) {
                throw new IllegalStateException("missing SPIR-V resource: " + SHADER_DIR + name);
            }
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("failed to read SPIR-V resource: " + SHADER_DIR + name, e);
        }
        ByteBuffer code = MemoryUtil.memAlloc(bytes.length).put(bytes);
        code.flip();
        try {
            VkShaderModuleCreateInfo smci = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
            LongBuffer pModule = stack.mallocLong(1);
            check(VK10.vkCreateShaderModule(vk, smci, null, pModule), "vkCreateShaderModule(" + name + ")");
            return pModule.get(0);
        } finally {
            MemoryUtil.memFree(code);
        }
    }
}
