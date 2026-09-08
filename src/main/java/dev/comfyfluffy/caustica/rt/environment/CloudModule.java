package dev.comfyfluffy.caustica.rt.environment;

import com.mojang.blaze3d.platform.NativeImage;
import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.CausticaMod;
import dev.comfyfluffy.caustica.rt.gen.WorldPushData.Float4;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttributes;

import java.io.InputStream;
import java.util.Optional;

/** Owns cloud configuration materialization and the authored classic-deck cache. */
public final class CloudModule {
    public static final CloudModule INSTANCE = new CloudModule();

    private static final int DIMENSION_OVERWORLD = 0;
    private static final int STYLE_VOLUMETRIC = 1;
    private static final double FIELD_PERIOD_BLOCKS = 512.0 * 12.0 * 2.0 / 0.5;
    private static final double WIND_BLOCKS_PER_TICK = 0.03;
    private static final float MAX_THICKNESS_BLOCKS = 110.0f;
    private static final float CLASSIC_MIN_THICKNESS = 4.0f;
    private static final double Z_OFFSET_BLOCKS = 3.96;
    private static final float VIEW_LIMIT_BLOCKS = 3072.0f;
    private static final float VIEW_LIMIT_HEIGHT_MULTIPLE = 6.0f;

    private static final Identifier CLOUDS_LOCATION =
            Identifier.withDefaultNamespace("textures/environment/clouds.png");
    public static final int MAP_CELLS = 256;
    public static final int MAP_WORDS = MAP_CELLS * MAP_CELLS / 32;
    public static final int MAP_BYTES = MAP_WORDS * 4;

    private volatile int[] packedCells;
    private volatile boolean cellLoadFailed;

    private CloudModule() {
    }

    /** Read live feature configuration, preserving the legacy next-frame toggle timing. */
    public boolean enabled() {
        return CausticaConfig.Rt.Composite.CLOUDS.value();
    }

    public boolean volumetric() {
        return CausticaConfig.Rt.Composite.cloudStyleIndex() == STYLE_VOLUMETRIC;
    }

    /** Materialize all cloud push lanes together for one environment snapshot. */
    public EnvironmentParameters.Clouds parameters(
            int dimension,
            EnvironmentParameters.Weather weather,
            double cameraX,
            double cameraY,
            double cameraZ,
            EnvironmentParameters.Time time) {
        float coverage = CausticaConfig.Rt.Composite.CLOUD_COVERAGE.value();
        float opacity = CausticaConfig.Rt.Composite.CLOUD_OPACITY.value();
        float shadow = CausticaConfig.Rt.Composite.CLOUD_SHADOW_STRENGTH.value();
        if (dimension != DIMENSION_OVERWORLD) {
            return EnvironmentParameters.Clouds.NONE;
        }

        float fill = Math.min(1f, weather.rain() + weather.thunder());
        float height = CausticaConfig.Rt.Composite.CLOUD_HEIGHT.value();
        double drift = time.gameTimeTicks() * WIND_BLOCKS_PER_TICK;
        double anchorX = cameraX + drift;
        double anchorZ = cameraZ + Z_OFFSET_BLOCKS;
        float thickness = Math.clamp(CausticaConfig.Rt.Composite.CLOUD_THICKNESS.value(), 0f, 1f)
                * MAX_THICKNESS_BLOCKS;
        if (!volumetric()) {
            thickness = Math.max(CLASSIC_MIN_THICKNESS, thickness);
        }
        float deckCentre = height + thickness * 0.5f;
        return new EnvironmentParameters.Clouds(
                new Float4(Math.clamp(coverage, 0f, 1f), Math.clamp(opacity, 0f, 1f),
                        Math.clamp(shadow, 0f, 1f), (float) (deckCentre - cameraY)),
                new Float4(wrapAnchor(anchorX), wrapAnchor(anchorZ), thickness,
                        viewLimit(deckCentre - (float) cameraY)),
                cloudColor(fill));
    }

    /** Drop authored cloud history; the next frame retries the resource-pack texture. */
    public void invalidate() {
        packedCells = null;
        cellLoadFailed = false;
    }

    /** Cached vanilla cloud occupancy, or null when the noise fallback must be used. */
    public int[] cells() {
        int[] local = packedCells;
        if (local == null && !cellLoadFailed) {
            local = loadCells();
            packedCells = local;
            if (local == null) {
                cellLoadFailed = true;
            }
        }
        return local;
    }

    private static Float4 cloudColor(float weatherFill) {
        float r = 1f, g = 1f, b = 1f;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameRenderer != null) {
                float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                int argb = mc.gameRenderer.mainCamera().attributeProbe()
                        .getValue(EnvironmentAttributes.CLOUD_COLOR, partialTick);
                r = srgb8ToLinear(ARGB.red(argb));
                g = srgb8ToLinear(ARGB.green(argb));
                b = srgb8ToLinear(ARGB.blue(argb));
            }
        } catch (Throwable ignored) {
            // Early boot or unsupported probe: white is the legacy neutral fallback.
        }
        return new Float4(r, g, b, Math.clamp(weatherFill, 0f, 1f));
    }

    static float srgb8ToLinear(int value8) {
        float v = (value8 & 0xFF) / 255.0f;
        return v <= 0.04045f ? v / 12.92f : (float) Math.pow((v + 0.055f) / 1.055f, 2.4f);
    }

    static float viewLimit(float deckAboveCamera) {
        return Math.max(VIEW_LIMIT_BLOCKS, Math.abs(deckAboveCamera) * VIEW_LIMIT_HEIGHT_MULTIPLE);
    }

    static float wrapAnchor(double blocks) {
        double wrapped = blocks % FIELD_PERIOD_BLOCKS;
        if (wrapped < 0.0) {
            wrapped += FIELD_PERIOD_BLOCKS;
        }
        return (float) wrapped;
    }

    private static int[] loadCells() {
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager()
                    .getResource(CLOUDS_LOCATION);
            if (resource.isEmpty()) {
                return null;
            }
            try (InputStream input = resource.get().open(); NativeImage image = NativeImage.read(input)) {
                if (image.getWidth() != MAP_CELLS || image.getHeight() != MAP_CELLS) {
                    CausticaMod.LOGGER.warn("clouds.png is {}x{}, not {}x{}; the ray-traced classic "
                                    + "deck falls back to its noise field",
                            image.getWidth(), image.getHeight(), MAP_CELLS, MAP_CELLS);
                    return null;
                }
                int[] words = new int[MAP_WORDS];
                int occupied = 0;
                for (int y = 0; y < MAP_CELLS; y++) {
                    for (int x = 0; x < MAP_CELLS; x++) {
                        if (ARGB.alpha(image.getPixel(x, y)) >= 10) {
                            int index = y * MAP_CELLS + x;
                            words[index >> 5] |= 1 << (index & 31);
                            occupied++;
                        }
                    }
                }
                CausticaMod.LOGGER.info("RT cloud cell map loaded: {} of {} cells occupied",
                        occupied, MAP_CELLS * MAP_CELLS);
                return words;
            }
        } catch (Throwable t) {
            CausticaMod.LOGGER.warn("Failed to load clouds.png for the RT classic deck", t);
            return null;
        }
    }
}
