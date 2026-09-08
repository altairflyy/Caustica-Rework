package dev.comfyfluffy.caustica.rt.graph;

import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

/** A/B emitter. Generated hazards retain the exact broad legacy memory scope. */
public final class PostImageBarriers {
    // Verified from Minecraft 26.2 VulkanCommandEncoder.memoryBarrier bytecode.
    public static final long STAGES = VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
    public static final long ACCESS = VK10.VK_ACCESS_MEMORY_READ_BIT | VK10.VK_ACCESS_MEMORY_WRITE_BIT;
    private PostImageBarriers() {}

    public static void before(VkCommandBuffer command, MemoryStack stack,
                              PostBarrierPlan plan, String operation, boolean generated) {
        if (!generated) {
            VulkanCommandEncoder.memoryBarrier(command, stack);
            return;
        }
        if (!plan.before(operation).required()) return;
        KHRSynchronization2.vkCmdPipelineBarrier2KHR(command, dependency(stack));
    }

    static VkDependencyInfo dependency(MemoryStack stack) {
        VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack).sType$Default()
                .srcStageMask(STAGES).srcAccessMask(ACCESS).dstStageMask(STAGES).dstAccessMask(ACCESS);
        return VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(barrier);
    }
}
