package dev.comfyfluffy.caustica.rt.lighting;

import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtSharc;
import dev.comfyfluffy.caustica.rt.gen.WorldPushData.Float4;
import dev.comfyfluffy.caustica.rt.gen.WorldPushData.Int4;
import dev.comfyfluffy.caustica.rt.terrain.RtTerrain;
import net.minecraft.client.multiplayer.ClientLevel;

/** Host-side SHaRC ownership facade; RtSharc and sharc.slang retain implementation details. */
public final class SharcRadianceCache {
    /** Runtime owner used by the composite; tests may still create isolated instances. */
    public static final SharcRadianceCache INSTANCE = new SharcRadianceCache();

    private final RtSharc implementation = RtSharc.INSTANCE;
    private ClientLevel previousWorld;
    private int previousDimension;
    private boolean debugActive;
    private long lastDebugFrame;

    public boolean enabled() { return implementation.enabled(); }
    public void ensure(RtContext ctx) { implementation.ensure(ctx); }
    public void requestClear() { implementation.requestClear(); }
    public boolean clearRequested() { return implementation.clearRequested(); }
    public void clearNow(RtContext ctx) { implementation.clearNow(ctx); }
    public void destroy(RtContext ctx) { implementation.destroy(ctx); }
    public void releaseIfDisabled(RtContext ctx) { implementation.releaseIfDisabled(ctx); }
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

    /** Materializes the live WorldPush.sharcParams values at the existing render seam. */
    public Float4 params() {
        return new Float4(
                CausticaConfig.Rt.Sharc.CELL_SIZE.value(),
                CausticaConfig.Rt.Sharc.STRENGTH.value(),
                CausticaConfig.Rt.Sharc.TEMPORAL_BLEND.value(),
                CausticaConfig.Rt.Sharc.MAX_DISTANCE.value());
    }

    /** Materializes the live WorldPush.sharcParams2 values at the existing render seam. */
    public Float4 params2() {
        return new Float4(
                CausticaConfig.Rt.Sharc.START_BOUNCE.value(),
                CausticaConfig.Rt.Sharc.UPDATE_COVERAGE.value(),
                CausticaConfig.Rt.Sharc.FRAME_LIFETIME.value(),
                CausticaConfig.Rt.Sharc.NORMAL_THRESHOLD.value());
    }

    /** Materializes the live WorldPush.sharcParams3 values at the existing render seam. */
    public Float4 params3() {
        return new Float4(CausticaConfig.Rt.Sharc.STABLE_FRAMES.value(), 0.0f, 0.0f, 0.0f);
    }

    /** Returns the cache address needed by the existing shader push contract. */
    public long cacheAddress() { return implementation.address(); }

    /** Materializes the world-space cache origin without moving terrain anchoring into SHaRC. */
    public Int4 gridOrigin(RtTerrain terrain) {
        return new Int4(terrain.blockX, terrain.blockY, terrain.blockZ, implementation.entryCount());
    }

    /** Materializes the unchanged debug summary from the live SHaRC configuration and state. */
    public String debugDescription() {
        return "cell=" + CausticaConfig.Rt.Sharc.CELL_SIZE.value()
                + " blocks, entries=" + implementation.entryCount()
                + ", coverage=" + CausticaConfig.Rt.Sharc.UPDATE_COVERAGE.value()
                + ", blend=" + CausticaConfig.Rt.Sharc.TEMPORAL_BLEND.value()
                + ", startBounce=" + CausticaConfig.Rt.Sharc.START_BOUNCE.value()
                + ", strength=" + CausticaConfig.Rt.Sharc.STRENGTH.value()
                + ", lifetime=" + CausticaConfig.Rt.Sharc.FRAME_LIFETIME.value()
                + ", normal=" + CausticaConfig.Rt.Sharc.NORMAL_THRESHOLD.value()
                + ", minSamples=" + CausticaConfig.Rt.Sharc.STABLE_FRAMES.value()
                + ", cacheAddr=0x" + Long.toHexString(implementation.address());
    }
}
