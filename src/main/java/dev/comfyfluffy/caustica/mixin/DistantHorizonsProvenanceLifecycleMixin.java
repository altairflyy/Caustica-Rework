package dev.comfyfluffy.caustica.mixin;

import dev.comfyfluffy.caustica.compat.DhMaterialProvenance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Clears any abandoned worker-local state before DH starts one exact section build. */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.render.QuadTree.LodRenderSection", remap = false)
public abstract class DistantHorizonsProvenanceLifecycleMixin {
    @Inject(method = "getAndBuildRenderData", at = @At("HEAD"), require = 0)
    private void caustica$beginProvenanceBuild(CallbackInfoReturnable<?> cir) {
        DhMaterialProvenance.beginBuild();
    }
}
