package dev.comfyfluffy.caustica.rt.terrain;

import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.CausticaMod;
import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtFrameStats;
import dev.comfyfluffy.caustica.rt.RtGpuProfiler;

import java.util.Locale;

/** Shared producer gate for vanilla terrain and DH/Voxy background AS work. */
public final class BackgroundAsPacing {
    private static final BackgroundAsPacing INSTANCE = new BackgroundAsPacing();
    private static final long LOG_INTERVAL_NANOS = 5_000_000_000L;
    private final BackgroundAsPacingPolicy policy = new BackgroundAsPacingPolicy();
    private long lastTerrainDispatchNanos;
    private long lastLodDispatchNanos;
    private long lastLogNanos;
    private volatile BackgroundAsPacingPolicy.State state = BackgroundAsPacingPolicy.State.NORMAL;

    private BackgroundAsPacing() {
    }

    public static boolean allowTerrain(RtContext ctx) {
        return INSTANCE.allow(ctx, true);
    }

    public static boolean allowLod(RtContext ctx) {
        return INSTANCE.allow(ctx, false);
    }

    public static void observe(RtContext ctx) {
        INSTANCE.refresh(ctx);
    }

    public static boolean busy() {
        return INSTANCE.state == BackgroundAsPacingPolicy.State.GPU_BUSY;
    }

    private boolean allow(RtContext ctx, boolean terrain) {
        if (!CausticaConfig.Rt.Terrain.AS_PACING_ENABLED.value()) {
            state = BackgroundAsPacingPolicy.State.NORMAL;
            return true;
        }
        long now = System.nanoTime();
        boolean busy = refresh(ctx);
        if (!busy) {
            return true;
        }
        long interval = Math.multiplyExact(
                (long) CausticaConfig.Rt.Terrain.AS_PACING_BUSY_INTERVAL_MS.value(), 1_000_000L);
        long last = terrain ? lastTerrainDispatchNanos : lastLodDispatchNanos;
        if (now - last < interval) {
            return false;
        }
        if (terrain) {
            lastTerrainDispatchNanos = now;
        } else {
            lastLodDispatchNanos = now;
        }
        return true;
    }

    private boolean refresh(RtContext ctx) {
        if (!CausticaConfig.Rt.Terrain.AS_PACING_ENABLED.value()) {
            state = BackgroundAsPacingPolicy.State.NORMAL;
            return false;
        }
        long now = System.nanoTime();
        RtGpuProfiler profiler = ctx.gpuProfiler();
        double totalGpuMillis = profiler.recentTotalGpuMillis();
        long stableNanos = Math.multiplyExact(
                (long) CausticaConfig.Rt.Terrain.AS_PACING_RESUME_STABLE_MS.value(), 1_000_000L);
        BackgroundAsPacingPolicy.Decision decision = policy.update(totalGpuMillis, now,
                CausticaConfig.Rt.Terrain.AS_PACING_BUSY_ENTER_MS.value(),
                CausticaConfig.Rt.Terrain.AS_PACING_BUSY_EXIT_MS.value(), stableNanos);
        state = decision.state();
        if (RtFrameStats.enabled()) {
            RtFrameStats.FRAME.count("backgroundAsThrottleState", decision.busy() ? 1 : 0);
            RtFrameStats.FRAME.count("queuedGpuBuildCount", ctx.gpuExecutor().pendingJobCount());
        }
        if (now - lastLogNanos >= LOG_INTERVAL_NANOS || lastLogNanos == 0L) {
            lastLogNanos = now;
            CausticaMod.LOGGER.info("[Caustica AS Pacing] state={} totalGpuMs={} terrainInFlight={} lodState={} queuedGpuBuilds={}",
                    decision.state(), Double.isFinite(totalGpuMillis)
                            ? String.format(Locale.ROOT, "%.3f", totalGpuMillis) : "n/a",
                    RtTerrain.terrainInFlight(), RtLodTerrain.schedulerState(), ctx.gpuExecutor().pendingJobCount());
        }
        return decision.busy();
    }
}
