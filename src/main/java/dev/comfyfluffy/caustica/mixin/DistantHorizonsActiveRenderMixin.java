package dev.comfyfluffy.caustica.mixin;

import dev.comfyfluffy.caustica.compat.DistantHorizonsCompat;
import dev.comfyfluffy.caustica.client.VanillaRenderController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes the exact post-cull LodBufferContainer set used by DH's native draw. */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.render.blaze.BlazeDhTerrainRenderer", remap = false)
public abstract class DistantHorizonsActiveRenderMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void caustica$publishExactRasterSet(@Coerce Object renderParams, boolean transparent,
                                                @Coerce Object exactContainers,
                                                @Coerce Object profiler, CallbackInfo ci) {
        VanillaRenderController.INSTANCE.markNativeDhHookObserved();
        DistantHorizonsCompat.publishActiveRasterContainers(exactContainers, transparent);
    }
}
