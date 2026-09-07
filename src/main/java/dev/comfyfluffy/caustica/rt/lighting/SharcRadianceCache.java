package dev.comfyfluffy.caustica.rt.lighting;

import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtSharc;
import net.minecraft.client.multiplayer.ClientLevel;

/** Host-side SHaRC ownership facade; RtSharc and sharc.slang retain implementation details. */
public final class SharcRadianceCache {
    private final RtSharc implementation = RtSharc.INSTANCE;
    private ClientLevel previousWorld;
    private int previousDimension;
    private boolean debugActive;
    private long lastDebugFrame;

    public RtSharc implementation() { return implementation; }
    public boolean enabled() { return implementation.enabled(); }
    public void ensure(RtContext ctx) { implementation.ensure(ctx); }
    public void requestClear() { implementation.requestClear(); }
    public boolean clearRequested() { return implementation.clearRequested(); }
    public void clearNow(RtContext ctx) { implementation.clearNow(ctx); }
    public void releaseIfDisabled(RtContext ctx) { implementation.releaseIfDisabled(ctx); }
    public long address() { return implementation.address(); }
    public int entryCount() { return implementation.entryCount(); }
    public boolean sceneChanged(ClientLevel world, int dimension) {
        boolean changed = previousWorld != null && (previousWorld != world || previousDimension != dimension);
        previousWorld = world;
        previousDimension = dimension;
        return changed;
    }
    public boolean debugWasActive() { return debugActive; }
    public void setDebugActive(boolean active) { debugActive = active; }
    public long lastDebugFrame() { return lastDebugFrame; }
    public void setLastDebugFrame(long frame) { lastDebugFrame = frame; }
    public void resetTracking() { previousWorld = null; previousDimension = 0; debugActive = false; lastDebugFrame = 0; }
}
