package dev.comfyfluffy.caustica.mixin;

import dev.comfyfluffy.caustica.rt.RtFramePresenter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;
import java.util.Locale;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
    @ModifyArg(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;extractLines(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/util/List;Z)V", ordinal = 0),
            index = 1)
    private List<String> caustica$appendFrameGenerationPresentRate(List<String> lines) {
        RtFramePresenter.PresentRateSnapshot rate = RtFramePresenter.INSTANCE.presentRateSnapshot();
        if (rate.isFresh(System.nanoTime())) {
            lines.add(String.format(Locale.ROOT,
                    "Caustica FG: real %.1f + generated %.1f = presented %.1f FPS",
                    rate.realFps(), rate.generatedFps(), rate.totalPresentFps()));
        }
        var dhQuality = dev.comfyfluffy.caustica.compat.DistantHorizonsCompat.dhLodQuality();
        lines.add(String.format(Locale.ROOT,
                "Caustica DH canonical RT: hook=%s ring=%s distance=%d quality=%s/%s active=%d unmatched=%d sources=%d BLAS=%d instances=%d state=%s",
                dev.comfyfluffy.caustica.client.VanillaRenderController.INSTANCE
                        .wasNativeDhHookObservedThisFrame() ? "YES" : "NO",
                dev.comfyfluffy.caustica.compat.DistantHorizonsCompat.dhRtRingEnabled() ? "ON" : "OFF",
                dev.comfyfluffy.caustica.compat.DistantHorizonsCompat.dhRenderDistanceChunks(),
                dhQuality.maxHorizontalResolution(), dhQuality.horizontalQuality(),
                dev.comfyfluffy.caustica.compat.DistantHorizonsCompat.activeRasterContainerCount(),
                dev.comfyfluffy.caustica.compat.DistantHorizonsCompat.activeUnmatchedContainerCount(),
                dev.comfyfluffy.caustica.rt.terrain.RtLodTerrain.dhSourceCount(),
                dev.comfyfluffy.caustica.rt.terrain.RtLodTerrain.dhBlasCount(),
                dev.comfyfluffy.caustica.rt.terrain.RtLodTerrain.frameInstanceCount(),
                dev.comfyfluffy.caustica.rt.terrain.RtLodTerrain.schedulerState()));
        return lines;
    }
}
