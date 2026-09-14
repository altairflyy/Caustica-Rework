package dev.comfyfluffy.caustica.rt;

import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.CausticaMod;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkQueryPoolCreateInfo;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Opt-in, non-blocking Vulkan timestamp profiler for the RT command stream.
 *
 * <p>The graphics query pool is split into a ring. A slot is reset only after all timestamps written
 * into its previous use report availability; otherwise another slot is tried and profiling is skipped
 * when the ring is full. No WAIT query flag, fence wait, queue wait, or device wait is issued. Terrain
 * builds use a separate two-query pool on the executor's serial compute lane and are collected only
 * after that lane's existing timeline-completion wait.</p>
 */
public final class RtGpuProfiler {
    static final int WINDOW_FRAMES = 120;
    private static final int GRAPHICS_RING_SIZE = 8;
    private static final int QUERIES_PER_REGION = 2;
    private static final int GRAPHICS_QUERY_COUNT = Region.values().length * QUERIES_PER_REGION;
    private static final long RESULT_STRIDE = 2L * Long.BYTES;

    public enum Region {
        TOTAL("total"),
        ENTITY_BLAS("entityBLAS"),
        TLAS("TLAS"),
        PRIMARY("primary"),
        INDIRECT("indirect"),
        DLSS_RR("DLSS-RR"),
        NRD("NRD"),
        SVGF("SVGF"),
        UPSCALE("upscale"),
        EXPOSURE("exposure"),
        EXPOSURE_HISTOGRAM("exposureHistogram"),
        EXPOSURE_RESOLVE("exposureResolve"),
        DISPLAY("display"),
        COPY("copy");

        final String label;

        Region(String label) {
            this.label = label;
        }
    }

    private final RtContext ctx;
    private final double timestampPeriodNanos;
    private final int graphicsValidBits;
    private final int computeValidBits;
    private final Slot[] graphicsSlots = new Slot[GRAPHICS_RING_SIZE];
    private final List<double[]> graphicsSamples = new ArrayList<>(WINDOW_FRAMES);
    private final List<Double> terrainSamples = new ArrayList<>();
    private long graphicsPool;
    private long computePool;
    private int nextGraphicsSlot;
    private boolean unsupportedLogged;
    private volatile double recentTotalGpuMillis = Double.NaN;

    RtGpuProfiler(RtContext ctx, double timestampPeriodNanos, int graphicsValidBits, int computeValidBits) {
        this.ctx = ctx;
        this.timestampPeriodNanos = timestampPeriodNanos;
        this.graphicsValidBits = graphicsValidBits;
        this.computeValidBits = computeValidBits;
        for (int i = 0; i < graphicsSlots.length; i++) {
            graphicsSlots[i] = new Slot();
        }
    }

    public Session beginGraphicsFrame(VkCommandBuffer command) {
        if (!enabled() || graphicsValidBits == 0 || timestampPeriodNanos <= 0.0) {
            logUnsupportedOnce(graphicsValidBits);
            return Session.NOOP;
        }
        ensurePools();
        for (int attempt = 0; attempt < GRAPHICS_RING_SIZE; attempt++) {
            int slotIndex = (nextGraphicsSlot + attempt) % GRAPHICS_RING_SIZE;
            Slot slot = graphicsSlots[slotIndex];
            if (slot.pending && !collectGraphics(slotIndex, slot)) {
                continue;
            }
            nextGraphicsSlot = (slotIndex + 1) % GRAPHICS_RING_SIZE;
            int firstQuery = slotIndex * GRAPHICS_QUERY_COUNT;
            VK10.vkCmdResetQueryPool(command, graphicsPool, firstQuery, GRAPHICS_QUERY_COUNT);
            slot.usedMask = 0L;
            slot.pending = false;
            Session session = new Session(this, command, slotIndex, firstQuery);
            session.begin(Region.TOTAL);
            return session;
        }
        return Session.NOOP;
    }

    /** Start one serial async-compute batch. Its timing is reported separately as terrainGpu. */
    public ComputeSession beginTerrainBatch(VkCommandBuffer command) {
        if (!enabled() || computeValidBits == 0 || timestampPeriodNanos <= 0.0) {
            logUnsupportedOnce(computeValidBits);
            return ComputeSession.NOOP;
        }
        ensurePools();
        VK10.vkCmdResetQueryPool(command, computePool, 0, 2);
        VK10.vkCmdWriteTimestamp(command, VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, computePool, 0);
        return new ComputeSession(this, command, true);
    }

    /** Read a terrain batch only after the executor's pre-existing timeline completion. Never waits. */
    public void collectTerrainBatch(ComputeSession session) {
        if (!session.active) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer data = stack.mallocLong(4);
            int rc = VK10.vkGetQueryPoolResults(ctx.vk(), computePool, 0, 2, data, RESULT_STRIDE,
                    VK10.VK_QUERY_RESULT_64_BIT | VK10.VK_QUERY_RESULT_WITH_AVAILABILITY_BIT);
            if (rc != VK10.VK_SUCCESS || data.get(1) == 0L || data.get(3) == 0L) {
                return;
            }
            double millis = ticksToMillis(timestampDelta(data.get(0), data.get(2), computeValidBits));
            synchronized (terrainSamples) {
                terrainSamples.add(millis);
            }
        }
    }

    /** Most recently completed non-blocking graphics timing window, or NaN when unavailable. */
    public double recentTotalGpuMillis() {
        return recentTotalGpuMillis;
    }

    private boolean collectGraphics(int slotIndex, Slot slot) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int firstQuery = slotIndex * GRAPHICS_QUERY_COUNT;
            double[] sample = new double[Region.values().length];
            Arrays.fill(sample, Double.NaN);
            for (Region region : Region.values()) {
                if ((slot.usedMask & bit(region)) == 0L) continue;
                int q = region.ordinal() * QUERIES_PER_REGION;
                LongBuffer data = stack.mallocLong(4);
                int rc = VK10.vkGetQueryPoolResults(ctx.vk(), graphicsPool, firstQuery + q,
                        QUERIES_PER_REGION, data, RESULT_STRIDE,
                        VK10.VK_QUERY_RESULT_64_BIT | VK10.VK_QUERY_RESULT_WITH_AVAILABILITY_BIT);
                if (rc != VK10.VK_SUCCESS || data.get(1) == 0L || data.get(3) == 0L) {
                    return false;
                }
                sample[region.ordinal()] = ticksToMillis(timestampDelta(
                        data.get(0), data.get(2), graphicsValidBits));
            }
            addGraphicsSample(sample);
            slot.pending = false;
            return true;
        }
    }

    private synchronized void addGraphicsSample(double[] sample) {
        graphicsSamples.add(sample);
        if (graphicsSamples.size() < WINDOW_FRAMES) {
            return;
        }
        List<Double> terrain;
        synchronized (terrainSamples) {
            terrain = List.copyOf(terrainSamples);
            terrainSamples.clear();
        }
        StringBuilder log = new StringBuilder("[Caustica GPU] samples=").append(graphicsSamples.size());
        appendStats(log, "total", values(Region.TOTAL));
        appendDerived(log, "AS", Region.ENTITY_BLAS, Region.TLAS);
        appendStats(log, "terrainGpu", terrain);
        for (Region region : Region.values()) {
            if (region == Region.TOTAL || region == Region.EXPOSURE_HISTOGRAM
                    || region == Region.EXPOSURE_RESOLVE) continue;
            appendStats(log, region.label, values(region));
        }
        appendStats(log, Region.EXPOSURE_HISTOGRAM.label, values(Region.EXPOSURE_HISTOGRAM));
        appendStats(log, Region.EXPOSURE_RESOLVE.label, values(Region.EXPOSURE_RESOLVE));
        appendOther(log);
        CausticaMod.LOGGER.info(log.toString());
        List<Double> totals = values(Region.TOTAL);
        if (!totals.isEmpty()) {
            double sum = 0.0;
            for (double value : totals) sum += value;
            recentTotalGpuMillis = sum / totals.size();
        }
        graphicsSamples.clear();
    }

    private List<Double> values(Region region) {
        List<Double> values = new ArrayList<>(graphicsSamples.size());
        for (double[] sample : graphicsSamples) {
            double value = sample[region.ordinal()];
            if (!Double.isNaN(value)) values.add(value);
        }
        return values;
    }

    private void appendDerived(StringBuilder log, String label, Region... regions) {
        List<Double> values = new ArrayList<>();
        for (double[] sample : graphicsSamples) {
            double sum = 0.0;
            boolean present = false;
            for (Region region : regions) {
                double value = sample[region.ordinal()];
                if (!Double.isNaN(value)) {
                    sum += value;
                    present = true;
                }
            }
            if (present) values.add(sum);
        }
        appendStats(log, label, values);
    }

    private void appendOther(StringBuilder log) {
        List<Double> values = new ArrayList<>();
        Region[] topLevel = {Region.ENTITY_BLAS, Region.TLAS, Region.PRIMARY, Region.INDIRECT,
                Region.DLSS_RR, Region.NRD, Region.SVGF, Region.UPSCALE,
                Region.EXPOSURE, Region.DISPLAY, Region.COPY};
        for (double[] sample : graphicsSamples) {
            double total = sample[Region.TOTAL.ordinal()];
            if (Double.isNaN(total)) continue;
            double accounted = 0.0;
            for (Region region : topLevel) {
                double value = sample[region.ordinal()];
                if (!Double.isNaN(value)) accounted += value;
            }
            values.add(Math.max(0.0, total - accounted));
        }
        appendStats(log, "other", values);
    }

    static void appendStats(StringBuilder out, String label, List<Double> source) {
        if (source.isEmpty()) return;
        List<Double> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.naturalOrder());
        double sum = 0.0;
        for (double value : sorted) sum += value;
        int p95Index = Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.95) - 1);
        out.append(' ').append(label).append("[min/avg/p95/max]=")
                .append(format(sorted.get(0))).append('/')
                .append(format(sum / sorted.size())).append('/')
                .append(format(sorted.get(p95Index))).append('/')
                .append(format(sorted.get(sorted.size() - 1))).append("ms");
    }

    static long timestampDelta(long start, long end, int validBits) {
        if (validBits <= 0 || validBits > 64) throw new IllegalArgumentException("validBits=" + validBits);
        if (validBits == 64) return end - start;
        long mask = (1L << validBits) - 1L;
        return (end - start) & mask;
    }

    private double ticksToMillis(long ticks) {
        double unsignedTicks = ticks >= 0 ? (double) ticks : (double) (ticks & Long.MAX_VALUE) + 0x1.0p63;
        return unsignedTicks * timestampPeriodNanos / 1_000_000.0;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static long bit(Region region) {
        return 1L << region.ordinal();
    }

    private static boolean enabled() {
        return RtFrameStats.enabled();
    }

    private synchronized void ensurePools() {
        if (graphicsPool != 0L) return;
        synchronized (ctx.deviceQueueHostLock()) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer handle = stack.mallocLong(1);
                VkQueryPoolCreateInfo graphicsInfo = VkQueryPoolCreateInfo.calloc(stack).sType$Default()
                        .queryType(VK10.VK_QUERY_TYPE_TIMESTAMP)
                        .queryCount(GRAPHICS_RING_SIZE * GRAPHICS_QUERY_COUNT);
                RtContext.check(VK10.vkCreateQueryPool(ctx.vk(), graphicsInfo, null, handle),
                        "vkCreateQueryPool(RT GPU graphics profiler)");
                graphicsPool = handle.get(0);
                VkQueryPoolCreateInfo computeInfo = VkQueryPoolCreateInfo.calloc(stack).sType$Default()
                        .queryType(VK10.VK_QUERY_TYPE_TIMESTAMP).queryCount(2);
                RtContext.check(VK10.vkCreateQueryPool(ctx.vk(), computeInfo, null, handle),
                        "vkCreateQueryPool(RT GPU terrain profiler)");
                computePool = handle.get(0);
                RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_QUERY_POOL, graphicsPool, "RT GPU profiler graphics ring");
                RtDebugLabels.name(ctx, VK10.VK_OBJECT_TYPE_QUERY_POOL, computePool, "RT GPU profiler terrain");
                CausticaMod.LOGGER.info("[Caustica GPU] enabled: window={} frames, ring={}, timestampPeriod={}ns, validBits graphics/compute={}/{}",
                        WINDOW_FRAMES, GRAPHICS_RING_SIZE, timestampPeriodNanos, graphicsValidBits, computeValidBits);
            }
        }
    }

    private void logUnsupportedOnce(int validBits) {
        if (enabled() && !unsupportedLogged && (validBits == 0 || timestampPeriodNanos <= 0.0)) {
            unsupportedLogged = true;
            CausticaMod.LOGGER.warn("[Caustica GPU] timestamp profiling unavailable: timestampPeriod={}ns, queue validBits={}",
                    timestampPeriodNanos, validBits);
        }
    }

    public synchronized void destroy() {
        if (graphicsPool != 0L) VK10.vkDestroyQueryPool(ctx.vk(), graphicsPool, null);
        if (computePool != 0L) VK10.vkDestroyQueryPool(ctx.vk(), computePool, null);
        graphicsPool = 0L;
        computePool = 0L;
    }

    private static final class Slot {
        long usedMask;
        boolean pending;
    }

    public static final class Session {
        static final Session NOOP = new Session(null, null, -1, 0);
        private final RtGpuProfiler owner;
        private final VkCommandBuffer command;
        private final int slotIndex;
        private final int firstQuery;
        private boolean finished;

        private Session(RtGpuProfiler owner, VkCommandBuffer command, int slotIndex, int firstQuery) {
            this.owner = owner;
            this.command = command;
            this.slotIndex = slotIndex;
            this.firstQuery = firstQuery;
        }

        public void begin(Region region) {
            if (owner == null || finished) return;
            owner.graphicsSlots[slotIndex].usedMask |= bit(region);
            VK10.vkCmdWriteTimestamp(command, VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    owner.graphicsPool, firstQuery + region.ordinal() * QUERIES_PER_REGION);
        }

        public void end(Region region) {
            if (owner == null || finished) return;
            VK10.vkCmdWriteTimestamp(command, VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                    owner.graphicsPool, firstQuery + region.ordinal() * QUERIES_PER_REGION + 1);
        }

        public void finish() {
            if (owner == null || finished) return;
            end(Region.TOTAL);
            owner.graphicsSlots[slotIndex].pending = true;
            finished = true;
        }

        public void abort() {
            if (owner == null) return;
            owner.graphicsSlots[slotIndex].pending = false;
            finished = true;
        }
    }

    public static final class ComputeSession {
        static final ComputeSession NOOP = new ComputeSession(null, null, false);
        private final RtGpuProfiler owner;
        private final VkCommandBuffer command;
        private final boolean active;

        private ComputeSession(RtGpuProfiler owner, VkCommandBuffer command, boolean active) {
            this.owner = owner;
            this.command = command;
            this.active = active;
        }

        public void finishRecording() {
            if (active) {
                VK10.vkCmdWriteTimestamp(command, VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                        owner.computePool, 1);
            }
        }
    }
}
