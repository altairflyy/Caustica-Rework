package dev.comfyfluffy.caustica.rt.reconstruction;

import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.accel.RtImage;
import org.lwjgl.vulkan.VK10;

/**
 * Runtime owner for SVGF temporal images and state.
 *
 * <p>The denoiser remains responsible for recording dispatches and managing its
 * descriptor bindings. This class owns only the images and temporal state that
 * those dispatches consume.</p>
 */
public final class SvgfResources {
    private RtImage historyPing;
    private RtImage historyPong;
    private RtImage momentsPing;
    private RtImage momentsPong;
    private RtImage filterPing;
    private RtImage filterPong;
    private RtImage previousViewZ;
    private RtImage previousNormal;
    private boolean writeToPing;
    private boolean hasHistory;
    private double previousCameraX;
    private double previousCameraY;
    private double previousCameraZ;

    /** Allocates the unchanged SVGF working set for one render extent. */
    public void allocate(RtContext ctx, int width, int height) {
        historyPing = ctx.createStorageImage(width, height, VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                "svgf history ping " + width + "x" + height);
        historyPong = ctx.createStorageImage(width, height, VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                "svgf history pong " + width + "x" + height);
        momentsPing = ctx.createStorageImage(width, height, VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                "svgf moments ping " + width + "x" + height);
        momentsPong = ctx.createStorageImage(width, height, VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                "svgf moments pong " + width + "x" + height);
        filterPing = ctx.createStorageImage(width, height, VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                "svgf filter ping " + width + "x" + height);
        filterPong = ctx.createStorageImage(width, height, VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                "svgf filter pong " + width + "x" + height);
        previousViewZ = ctx.createStorageImage(width, height, VK10.VK_FORMAT_R32_SFLOAT,
                "svgf prev viewZ " + width + "x" + height);
        previousNormal = ctx.createStorageImage(width, height, VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
                "svgf prev normal " + width + "x" + height);
        resetHistory();
        previousCameraX = previousCameraY = previousCameraZ = 0.0;
    }

    public RtImage historyPing() { return historyPing; }
    public RtImage historyPong() { return historyPong; }
    public RtImage momentsPing() { return momentsPing; }
    public RtImage momentsPong() { return momentsPong; }
    public RtImage filterPing() { return filterPing; }
    public RtImage filterPong() { return filterPong; }
    public RtImage previousViewZ() { return previousViewZ; }
    public RtImage previousNormal() { return previousNormal; }
    public boolean writeToPing() { return writeToPing; }
    public boolean hasHistory() { return hasHistory; }
    public double previousCameraX() { return previousCameraX; }
    public double previousCameraY() { return previousCameraY; }
    public double previousCameraZ() { return previousCameraZ; }

    public void resetHistory() {
        hasHistory = false;
        writeToPing = true;
    }

    public void markHistoryValid() { hasHistory = true; }

    public void flipHistory() { writeToPing = !writeToPing; }

    public void snapshotPreviousCamera(double x, double y, double z) {
        previousCameraX = x;
        previousCameraY = y;
        previousCameraZ = z;
    }

    public void destroy() {
        destroyImage(historyPing); historyPing = null;
        destroyImage(historyPong); historyPong = null;
        destroyImage(momentsPing); momentsPing = null;
        destroyImage(momentsPong); momentsPong = null;
        destroyImage(filterPing); filterPing = null;
        destroyImage(filterPong); filterPong = null;
        destroyImage(previousViewZ); previousViewZ = null;
        destroyImage(previousNormal); previousNormal = null;
        resetHistory();
        previousCameraX = previousCameraY = previousCameraZ = 0.0;
    }

    private static void destroyImage(RtImage image) {
        if (image != null) image.destroy();
    }
}
