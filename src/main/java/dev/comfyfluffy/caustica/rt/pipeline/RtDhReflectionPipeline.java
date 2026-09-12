package dev.comfyfluffy.caustica.rt.pipeline;

import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtDebugLabels;
import dev.comfyfluffy.caustica.rt.accel.RtBuffer;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRAccelerationStructure;
import org.lwjgl.vulkan.KHRRayTracingPipeline;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
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
import org.lwjgl.vulkan.VkRayTracingPipelineCreateInfoKHR;
import org.lwjgl.vulkan.VkRayTracingShaderGroupCreateInfoKHR;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkStridedDeviceAddressRegionKHR;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.lwjgl.vulkan.VkWriteDescriptorSetAccelerationStructureKHR;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static dev.comfyfluffy.caustica.rt.RtContext.check;

/**
 * Dedicated ray tracing pipeline for secondary water reflections over the Distant Horizons far-field proxy.
 */
public final class RtDhReflectionPipeline {
    private static final String SHADER_DIR = "/caustica/rt/";
    private static final int PUSH_BYTES = 128;
    private static final int RING = 2;

    private final RtContext ctx;
    private final long descriptorSetLayout;
    private final long descriptorPool;
    private final long[] descriptorSets;
    private int currentSet;
    private final long pipelineLayout;
    private final long pipeline;
    private final long sampler;
    private final RtBuffer sbt;
    private final long sbtStride;
    private boolean destroyed;

    private RtDhReflectionPipeline(RtContext ctx, long dsl, long pool, long[] sets, long layout,
                                   long pipeline, long sampler, RtBuffer sbt, long sbtStride) {
        this.ctx = ctx;
        this.descriptorSetLayout = dsl;
        this.descriptorPool = pool;
        this.descriptorSets = sets;
        this.pipelineLayout = layout;
        this.pipeline = pipeline;
        this.sampler = sampler;
        this.sbt = sbt;
        this.sbtStride = sbtStride;
    }

    public static RtDhReflectionPipeline create(RtContext ctx) {
        VkDevice vk = ctx.vk();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer binds = VkDescriptorSetLayoutBinding.calloc(5, stack);
            binds.get(0).binding(0).descriptorType(KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR)
                    .descriptorCount(1).stageFlags(KHRRayTracingPipeline.VK_SHADER_STAGE_RAYGEN_BIT_KHR);
            binds.get(1).binding(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(1).stageFlags(KHRRayTracingPipeline.VK_SHADER_STAGE_RAYGEN_BIT_KHR);
            binds.get(2).binding(2).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1).stageFlags(KHRRayTracingPipeline.VK_SHADER_STAGE_RAYGEN_BIT_KHR);
            binds.get(3).binding(3).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1).stageFlags(KHRRayTracingPipeline.VK_SHADER_STAGE_RAYGEN_BIT_KHR);
            binds.get(4).binding(4).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1).stageFlags(KHRRayTracingPipeline.VK_SHADER_STAGE_RAYGEN_BIT_KHR);

            VkDescriptorSetLayoutCreateInfo dslci = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default()
                    .pBindings(binds);
            LongBuffer p = stack.mallocLong(1);
            check(VK10.vkCreateDescriptorSetLayout(vk, dslci, null, p), "vkCreateDescriptorSetLayout(dh reflection)");
            long dsl = p.get(0);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_DESCRIPTOR_SET_LAYOUT, dsl, "dh reflection descriptor set layout");

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(3, stack);
            poolSizes.get(0).type(KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR)
                    .descriptorCount(RING);
            poolSizes.get(1).type(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(RING);
            poolSizes.get(2).type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(RING * 3);

            VkDescriptorPoolCreateInfo dpci = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                    .maxSets(RING).pPoolSizes(poolSizes);
            check(VK10.vkCreateDescriptorPool(vk, dpci, null, p), "vkCreateDescriptorPool(dh reflection)");
            long pool = p.get(0);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_DESCRIPTOR_POOL, pool, "dh reflection descriptor pool");

            LongBuffer layouts = stack.mallocLong(RING);
            for (int i = 0; i < RING; i++) layouts.put(i, dsl);
            VkDescriptorSetAllocateInfo dsai = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                    .descriptorPool(pool).pSetLayouts(layouts);
            LongBuffer pSets = stack.mallocLong(RING);
            check(VK10.vkAllocateDescriptorSets(vk, dsai, pSets), "vkAllocateDescriptorSets(dh reflection)");
            long[] sets = new long[RING];
            pSets.get(sets);
            for (int i = 0; i < RING; i++) {
                RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_DESCRIPTOR_SET, sets[i], "dh reflection descriptor set " + i);
            }

            int pcStages = KHRRayTracingPipeline.VK_SHADER_STAGE_RAYGEN_BIT_KHR
                    | KHRRayTracingPipeline.VK_SHADER_STAGE_MISS_BIT_KHR;
            VkPushConstantRange.Buffer pushRange = VkPushConstantRange.calloc(1, stack);
            pushRange.get(0).stageFlags(pcStages).offset(0).size(PUSH_BYTES);
            VkPipelineLayoutCreateInfo plci = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                    .pSetLayouts(stack.longs(dsl)).pPushConstantRanges(pushRange);
            check(VK10.vkCreatePipelineLayout(vk, plci, null, p), "vkCreatePipelineLayout(dh reflection)");
            long layout = p.get(0);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_PIPELINE_LAYOUT, layout, "dh reflection pipeline layout");

            long mGen = loadModule(vk, stack, "dh_reflection.rgen.spv");
            long mMiss = loadModule(vk, stack, "dh_reflection.rmiss.spv");
            long mHit = loadModule(vk, stack, "dh_reflection.rchit.spv");

            ByteBuffer entry = stack.UTF8("main");
            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(3, stack);
            stages.get(0).sType$Default().stage(KHRRayTracingPipeline.VK_SHADER_STAGE_RAYGEN_BIT_KHR).module(mGen).pName(entry);
            stages.get(1).sType$Default().stage(KHRRayTracingPipeline.VK_SHADER_STAGE_MISS_BIT_KHR).module(mMiss).pName(entry);
            stages.get(2).sType$Default().stage(KHRRayTracingPipeline.VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR).module(mHit).pName(entry);

            VkRayTracingShaderGroupCreateInfoKHR.Buffer groups = VkRayTracingShaderGroupCreateInfoKHR.calloc(3, stack);
            groups.get(0).sType$Default().type(KHRRayTracingPipeline.VK_RAY_TRACING_SHADER_GROUP_TYPE_GENERAL_KHR)
                    .generalShader(0)
                    .closestHitShader(KHRRayTracingPipeline.VK_SHADER_UNUSED_KHR)
                    .anyHitShader(KHRRayTracingPipeline.VK_SHADER_UNUSED_KHR)
                    .intersectionShader(KHRRayTracingPipeline.VK_SHADER_UNUSED_KHR);
            groups.get(1).sType$Default().type(KHRRayTracingPipeline.VK_RAY_TRACING_SHADER_GROUP_TYPE_GENERAL_KHR)
                    .generalShader(1)
                    .closestHitShader(KHRRayTracingPipeline.VK_SHADER_UNUSED_KHR)
                    .anyHitShader(KHRRayTracingPipeline.VK_SHADER_UNUSED_KHR)
                    .intersectionShader(KHRRayTracingPipeline.VK_SHADER_UNUSED_KHR);
            groups.get(2).sType$Default().type(KHRRayTracingPipeline.VK_RAY_TRACING_SHADER_GROUP_TYPE_TRIANGLES_HIT_GROUP_KHR)
                    .generalShader(KHRRayTracingPipeline.VK_SHADER_UNUSED_KHR)
                    .closestHitShader(2)
                    .anyHitShader(KHRRayTracingPipeline.VK_SHADER_UNUSED_KHR)
                    .intersectionShader(KHRRayTracingPipeline.VK_SHADER_UNUSED_KHR);

            VkRayTracingPipelineCreateInfoKHR.Buffer rtpci = VkRayTracingPipelineCreateInfoKHR.calloc(1, stack);
            rtpci.get(0).sType$Default().pStages(stages).pGroups(groups).maxPipelineRayRecursionDepth(1).layout(layout);

            LongBuffer pPipeline = stack.mallocLong(1);
            check(KHRRayTracingPipeline.vkCreateRayTracingPipelinesKHR(vk, VK10.VK_NULL_HANDLE,
                    VK10.VK_NULL_HANDLE, rtpci, null, pPipeline), "vkCreateRayTracingPipelinesKHR(dh reflection)");
            long pipeline = pPipeline.get(0);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_PIPELINE, pipeline, "dh reflection raytracing pipeline");

            VK10.vkDestroyShaderModule(vk, mGen, null);
            VK10.vkDestroyShaderModule(vk, mMiss, null);
            VK10.vkDestroyShaderModule(vk, mHit, null);

            int handleSize = ctx.shaderGroupHandleSize();
            ByteBuffer handles = stack.malloc(3 * handleSize);
            check(KHRRayTracingPipeline.vkGetRayTracingShaderGroupHandlesKHR(vk, pipeline, 0, 3, handles),
                    "vkGetRayTracingShaderGroupHandlesKHR(dh reflection)");
            long stride = align(handleSize, Math.max(ctx.shaderGroupBaseAlignment(), ctx.shaderGroupHandleAlignment()));
            RtBuffer sbt = ctx.createAlignedBuffer(stride * 3,
                    KHRRayTracingPipeline.VK_BUFFER_USAGE_SHADER_BINDING_TABLE_BIT_KHR, true,
                    "dh reflection SBT", ctx.shaderGroupBaseAlignment());
            for (int g = 0; g < 3; g++) {
                MemoryUtil.memCopy(MemoryUtil.memAddress(handles) + (long) g * handleSize, sbt.mapped + g * stride, handleSize);
            }
            sbt.flush();

            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack).sType$Default()
                    .magFilter(VK10.VK_FILTER_LINEAR).minFilter(VK10.VK_FILTER_LINEAR)
                    .mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .maxLod(0.0f);
            LongBuffer pSampler = stack.mallocLong(1);
            check(VK10.vkCreateSampler(vk, samplerInfo, null, pSampler), "vkCreateSampler(dh reflection)");
            long sampler = pSampler.get(0);
            RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_SAMPLER, sampler, "dh reflection sampler");

            return new RtDhReflectionPipeline(ctx, dsl, pool, sets, layout, pipeline, sampler, sbt, stride);
        }
    }

    private static long align(long value, long alignment) {
        return (value + alignment - 1L) & ~(alignment - 1L);
    }

    public void setImagesAndTlas(long tlasHandle, long reflectionImageView,
                                 long dhColorView, long dhDepthView, long dhWaterMaskView,
                                 int dhColorLayout, int dhDepthLayout, int dhWaterMaskLayout) {
        currentSet = (currentSet + 1) % RING;
        long set = descriptorSets[currentSet];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkWriteDescriptorSetAccelerationStructureKHR asWrite = VkWriteDescriptorSetAccelerationStructureKHR.calloc(stack)
                    .sType(KHRAccelerationStructure.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET_ACCELERATION_STRUCTURE_KHR)
                    .pAccelerationStructures(stack.longs(tlasHandle));

            VkDescriptorImageInfo.Buffer reflInfo = VkDescriptorImageInfo.calloc(1, stack);
            reflInfo.get(0).imageView(reflectionImageView).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);

            VkDescriptorImageInfo.Buffer colorInfo = VkDescriptorImageInfo.calloc(1, stack);
            colorInfo.get(0).sampler(sampler).imageView(dhColorView).imageLayout(dhColorLayout);

            VkDescriptorImageInfo.Buffer depthInfo = VkDescriptorImageInfo.calloc(1, stack);
            depthInfo.get(0).sampler(sampler).imageView(dhDepthView).imageLayout(dhDepthLayout);

            VkDescriptorImageInfo.Buffer waterMaskInfo = VkDescriptorImageInfo.calloc(1, stack);
            waterMaskInfo.get(0).sampler(sampler).imageView(dhWaterMaskView).imageLayout(dhWaterMaskLayout);

            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(5, stack);
            writes.get(0).sType$Default().dstSet(set).dstBinding(0)
                    .descriptorCount(1).descriptorType(KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR)
                    .pNext(asWrite.address());
            writes.get(1).sType$Default().dstSet(set).dstBinding(1)
                    .descriptorCount(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).pImageInfo(reflInfo);
            writes.get(2).sType$Default().dstSet(set).dstBinding(2)
                    .descriptorCount(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).pImageInfo(colorInfo);
            writes.get(3).sType$Default().dstSet(set).dstBinding(3)
                    .descriptorCount(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).pImageInfo(depthInfo);
            writes.get(4).sType$Default().dstSet(set).dstBinding(4)
                    .descriptorCount(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).pImageInfo(waterMaskInfo);

            VK10.vkUpdateDescriptorSets(ctx.vk(), writes, null);
        }
    }

    public void trace(VkCommandBuffer cmd, int width, int height,
                      Matrix4f nativeInvViewProj,
                      float lightDirX, float lightDirY, float lightDirZ,
                      float lightRadX, float lightRadY, float lightRadZ,
                      float ambientX, float ambientY, float ambientZ, float ambientW,
                      float nativeDepthClear, int renderWidth, int renderHeight) {
        try (MemoryStack stack = MemoryStack.stackPush();
             RtDebugLabels.Scope ignored = RtDebugLabels.scope(ctx, cmd, "dh reflection trace")) {
            VK10.vkCmdBindPipeline(cmd, KHRRayTracingPipeline.VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR, pipeline);
            VK10.vkCmdBindDescriptorSets(cmd, KHRRayTracingPipeline.VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR,
                    pipelineLayout, 0, stack.longs(descriptorSets[currentSet]), null);

            ByteBuffer push = stack.malloc(PUSH_BYTES);
            nativeInvViewProj.get(0, push);
            push.putFloat(64, lightDirX);
            push.putFloat(68, lightDirY);
            push.putFloat(72, lightDirZ);
            push.putFloat(76, 1.0f);
            push.putFloat(80, lightRadX);
            push.putFloat(84, lightRadY);
            push.putFloat(88, lightRadZ);
            push.putFloat(92, 1.0f);
            push.putFloat(96, ambientX);
            push.putFloat(100, ambientY);
            push.putFloat(104, ambientZ);
            push.putFloat(108, ambientW);
            push.putFloat(112, nativeDepthClear);
            push.putInt(116, renderWidth);
            push.putInt(120, renderHeight);
            push.putInt(124, 0);

            int pcStages = KHRRayTracingPipeline.VK_SHADER_STAGE_RAYGEN_BIT_KHR
                    | KHRRayTracingPipeline.VK_SHADER_STAGE_MISS_BIT_KHR;
            VK10.vkCmdPushConstants(cmd, pipelineLayout, pcStages, 0, push);

            VkStridedDeviceAddressRegionKHR raygenRegion = VkStridedDeviceAddressRegionKHR.calloc(stack);
            raygenRegion.deviceAddress(sbt.deviceAddress);
            raygenRegion.stride(sbtStride);
            raygenRegion.size(sbtStride);

            VkStridedDeviceAddressRegionKHR missRegion = VkStridedDeviceAddressRegionKHR.calloc(stack);
            missRegion.deviceAddress(sbt.deviceAddress + sbtStride);
            missRegion.stride(sbtStride);
            missRegion.size(sbtStride);

            VkStridedDeviceAddressRegionKHR hitRegion = VkStridedDeviceAddressRegionKHR.calloc(stack);
            hitRegion.deviceAddress(sbt.deviceAddress + 2 * sbtStride);
            hitRegion.stride(sbtStride);
            hitRegion.size(sbtStride);

            VkStridedDeviceAddressRegionKHR callableRegion = VkStridedDeviceAddressRegionKHR.calloc(stack);

            KHRRayTracingPipeline.vkCmdTraceRaysKHR(cmd, raygenRegion, missRegion, hitRegion, callableRegion, width, height, 1);
        }
    }

    public void destroy() {
        if (destroyed) return;
        VkDevice vk = ctx.vk();
        VK10.vkDestroyPipeline(vk, pipeline, null);
        VK10.vkDestroyPipelineLayout(vk, pipelineLayout, null);
        VK10.vkDestroySampler(vk, sampler, null);
        sbt.destroy();
        VK10.vkDestroyDescriptorPool(vk, descriptorPool, null);
        VK10.vkDestroyDescriptorSetLayout(vk, descriptorSetLayout, null);
        destroyed = true;
    }

    private static long loadModule(VkDevice vk, MemoryStack stack, String name) {
        byte[] bytes;
        try (InputStream in = RtDhReflectionPipeline.class.getResourceAsStream(SHADER_DIR + name)) {
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
