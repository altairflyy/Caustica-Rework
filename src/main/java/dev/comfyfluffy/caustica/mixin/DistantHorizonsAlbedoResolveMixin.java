package dev.comfyfluffy.caustica.mixin;

import dev.comfyfluffy.caustica.compat.DhMaterialProvenance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer", remap = false)
public abstract class DistantHorizonsAlbedoResolveMixin {
    @Inject(method = "transformFullDataToRenderSource", at = @At("RETURN"), require = 0)
    private static void caustica$captureMaterialProvenance(@Coerce Object fullSource,
                                                            @Coerce Object levelWrapper,
                                                            CallbackInfoReturnable<?> cir) {
        DhMaterialProvenance.capture(fullSource, levelWrapper, cir.getReturnValue());
    }
}
