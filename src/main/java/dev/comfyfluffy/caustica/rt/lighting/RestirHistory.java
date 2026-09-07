package dev.comfyfluffy.caustica.rt.lighting;

import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.accel.RtBuffer;
import dev.comfyfluffy.caustica.rt.gen.RestirReservoirData;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;

/** Owns the two-buffer ReSTIR history ping-pong; reservoir math remains in the legacy passes. */
public final class RestirHistory {
    private RtBuffer[] reservoirs = new RtBuffer[2];
    private int writeIndex;
    private boolean enabled;

    public void ensure(RtContext ctx, int renderW, int renderH, boolean desired) {
        boolean complete = reservoirs[0] != null && reservoirs[1] != null;
        if (desired == enabled && desired == complete) return;
        ctx.waitIdle();
        destroy();
        if (!desired) return;
        long bytes = Math.multiplyExact(Math.multiplyExact((long) renderW, (long) renderH), RestirReservoirData.BYTE_SIZE);
        try {
            reservoirs[0] = ctx.createBuffer(bytes, VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    false, "ReSTIR reservoir history A " + renderW + "x" + renderH);
            reservoirs[1] = ctx.createBuffer(bytes, VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    false, "ReSTIR reservoir history B " + renderW + "x" + renderH);
            writeIndex = 0;
            ctx.submitSync(cmd -> {
                VK10.vkCmdFillBuffer(cmd, reservoirs[0].handle, 0L, bytes, 0);
                VK10.vkCmdFillBuffer(cmd, reservoirs[1].handle, 0L, bytes, 0);
                try (MemoryStack stack = MemoryStack.stackPush()) { VulkanCommandEncoder.memoryBarrier(cmd, stack); }
            });
            enabled = true;
        } catch (Throwable failure) {
            destroy();
            throw failure;
        }
    }

    public long previousAddress() { return enabled ? reservoirs[writeIndex ^ 1].deviceAddress : 0L; }
    public long currentAddress() { return enabled ? reservoirs[writeIndex].deviceAddress : 0L; }
    public boolean enabled() { return enabled; }
    public void advance() { if (enabled) writeIndex ^= 1; }

    public void destroy() {
        for (int i = 0; i < reservoirs.length; i++) {
            if (reservoirs[i] != null) { reservoirs[i].destroy(); reservoirs[i] = null; }
        }
        writeIndex = 0;
        enabled = false;
    }
}
