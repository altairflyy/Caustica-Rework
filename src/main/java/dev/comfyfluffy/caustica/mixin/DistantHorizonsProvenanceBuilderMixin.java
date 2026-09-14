package dev.comfyfluffy.caustica.mixin;

import dev.comfyfluffy.caustica.compat.DhMaterialProvenance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Selects DH's exact center render source, excluding the four neighbour-only culling sources. */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.ColumnRenderBufferBuilder",
        remap = false)
public abstract class DistantHorizonsProvenanceBuilderMixin {
    @Inject(method = "makeLodRenderData", at = @At("HEAD"), require = 0)
    private static void caustica$bindExactProvenance(CallbackInfo ci) {
        DhMaterialProvenance.bindBuilder();
    }
}
