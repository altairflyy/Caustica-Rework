package dev.comfyfluffy.caustica.rt.graph;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

/** Generated upscale seam barriers preserve the conservative legacy memory scope. */
public final class UpscalerBarriers {
    private UpscalerBarriers() {}

    public static void before(VkCommandBuffer command, MemoryStack stack,
                              UpscalerBarrierPlan.Backend backend, String operation) {
        if (!UpscalerBarrierPlan.before(backend, operation).required()) return;
        VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack).sType$Default()
                .srcStageMask(VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT)
                .srcAccessMask(VK10.VK_ACCESS_MEMORY_READ_BIT | VK10.VK_ACCESS_MEMORY_WRITE_BIT)
                .dstStageMask(VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT)
                .dstAccessMask(VK10.VK_ACCESS_MEMORY_READ_BIT | VK10.VK_ACCESS_MEMORY_WRITE_BIT);
        KHRSynchronization2.vkCmdPipelineBarrier2KHR(command,
                VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(barrier));
    }
}
