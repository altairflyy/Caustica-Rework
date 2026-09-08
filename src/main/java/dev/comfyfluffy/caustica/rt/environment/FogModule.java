package dev.comfyfluffy.caustica.rt.environment;

import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.rt.gen.WorldPushData.Float4;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttributes;

/** Materializes the CPU configuration and binding lanes consumed by participating fog. */
public final class FogModule {
    public static final FogModule INSTANCE = new FogModule();

    private FogModule() {
    }

    public EnvironmentParameters.Fog parameters(float partialTick) {
        return new EnvironmentParameters.Fog(fogParams(), fogTint(partialTick));
    }

    private static Float4 fogParams() {
        if (!CausticaConfig.Rt.Composite.FOG_ENABLED.value()) {
            return new Float4(0.0f, 0.0f, 0.0f, 0.0f);
        }
        return new Float4(
                CausticaConfig.Rt.Composite.FOG_DENSITY.value(),
                CausticaConfig.Rt.Composite.FOG_ANISOTROPY.value(),
                CausticaConfig.Rt.Composite.FOG_DISTANCE.value(),
                CausticaConfig.Rt.Composite.FOG_HEIGHT_FALLOFF.value());
    }

    private static Float4 fogTint(float partialTick) {
        float strength = CausticaConfig.Rt.Composite.FOG_BIOME_TINT.value();
        if (!CausticaConfig.Rt.Composite.FOG_ENABLED.value() || strength <= 0f) {
            return new Float4(1f, 1f, 1f, 0f);
        }
        float r = 1f, g = 1f, b = 1f;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameRenderer != null) {
                int argb = mc.gameRenderer.mainCamera().attributeProbe()
                        .getValue(EnvironmentAttributes.FOG_COLOR, partialTick);
                r = srgb8ToLinear(ARGB.red(argb));
                g = srgb8ToLinear(ARGB.green(argb));
                b = srgb8ToLinear(ARGB.blue(argb));
            }
        } catch (Throwable ignored) {
            return new Float4(1f, 1f, 1f, 0f);
        }
        return new Float4(r, g, b, Math.clamp(strength, 0f, 1f));
    }

    static float srgb8ToLinear(int value8) {
        float v = (value8 & 0xFF) / 255.0f;
        return v <= 0.04045f ? v / 12.92f : (float) Math.pow((v + 0.055f) / 1.055f, 2.4f);
    }
}
