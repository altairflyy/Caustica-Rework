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
        lines.add(String.format(Locale.ROOT,
                "Caustica DH RT: dispatched=%b, proxyBLAS=%d, proxyInstances=%d",
                dev.comfyfluffy.caustica.rt.post.PostProcessing.isDhReflectionDispatchedThisFrame(),
                dev.comfyfluffy.caustica.rt.proxy.DhFarFieldProxy.get().activeTileCount(),
                dev.comfyfluffy.caustica.rt.proxy.DhFarFieldProxy.get().lastInstanceCount()));
        return lines;
    }
}
