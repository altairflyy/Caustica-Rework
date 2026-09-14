package dev.comfyfluffy.caustica.mixin;

import dev.comfyfluffy.caustica.compat.DhMaterialProvenance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;
import java.util.ArrayList;

/** Associates provenance with DH's exact post-merge buffer-list identities before upload. */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder",
        remap = false)
public abstract class DistantHorizonsProvenanceBuffersMixin {
    @Inject(method = "makeOpaqueVertexBuffers", at = @At("RETURN"), require = 0)
    private void caustica$captureOpaqueProvenance(CallbackInfoReturnable<ArrayList<ByteBuffer>> cir) {
        DhMaterialProvenance.captureBuiltBuffers(cir.getReturnValue(), false);
    }

    @Inject(method = "makeTransparentVertexBuffers", at = @At("RETURN"), require = 0)
    private void caustica$captureTransparentProvenance(CallbackInfoReturnable<ArrayList<ByteBuffer>> cir) {
        DhMaterialProvenance.captureBuiltBuffers(cir.getReturnValue(), true);
    }
}
