package dev.comfyfluffy.caustica.rt.post;

import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.accel.RtImage;
import org.lwjgl.vulkan.VkCommandBuffer;

/** Narrow presentation capability consumed by frame generation for generated-frame UI composition. */
public interface GeneratedFrameUiComposer {
    long uiSampler(RtContext ctx);

    void composeHdrGenerated(VkCommandBuffer command, RtImage target, long overlayView,
                             int width, int height);
}
