package dev.comfyfluffy.caustica.rt.reconstruction;

import dev.comfyfluffy.caustica.rt.accel.RtImage;

/** Ownership container for SVGF temporal images and parity state. Dispatch recording remains external. */
public final class SvgfResources {
    public RtImage historyPing;
    public RtImage historyPong;
    public RtImage momentsPing;
    public RtImage momentsPong;
    public RtImage filterPing;
    public RtImage filterPong;
    public RtImage previousViewZ;
    public RtImage previousNormal;
    public boolean writeToPing;
    public boolean hasHistory;
    public double previousCameraX;
    public double previousCameraY;
    public double previousCameraZ;

    public void resetHistory() {
        hasHistory = false;
        writeToPing = true;
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
