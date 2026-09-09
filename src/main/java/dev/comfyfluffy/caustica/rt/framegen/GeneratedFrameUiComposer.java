package dev.comfyfluffy.caustica.rt.framegen;

import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.accel.RtImage;
import org.lwjgl.vulkan.VkCommandBuffer;

/** Narrow presentation capability used to stamp UI onto generated frames. */
public interface GeneratedFrameUiComposer {
    long uiSampler(RtContext ctx);
    void composeHdrGenerated(VkCommandBuffer command, RtImage target, long overlayView,
                             int width, int height);
}
