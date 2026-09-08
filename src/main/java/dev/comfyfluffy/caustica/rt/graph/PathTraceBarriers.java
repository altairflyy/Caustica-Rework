package dev.comfyfluffy.caustica.rt.graph;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkMemoryBarrier2;

/** Generated path-trace barriers preserving the qualified broad scope. */
public final class PathTraceBarriers {
    public static final long STAGES = VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
    public static final long ACCESS = VK10.VK_ACCESS_MEMORY_READ_BIT | VK10.VK_ACCESS_MEMORY_WRITE_BIT;

    private PathTraceBarriers() {}

    public static void before(VkCommandBuffer command, MemoryStack stack,
                              PathTraceBarrierPlan plan, String operation) {
        if (!plan.before(operation).required()) return;
        VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack).sType$Default()
                .srcStageMask(STAGES).srcAccessMask(ACCESS)
                .dstStageMask(STAGES).dstAccessMask(ACCESS);
        KHRSynchronization2.vkCmdPipelineBarrier2KHR(command,
                VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(barrier));
    }
}
