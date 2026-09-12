package dev.comfyfluffy.caustica.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.comfyfluffy.caustica.compat.DistantHorizonsWaterMask;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.render.blaze.wrappers.RenderPipelineBuilderWrapper",
        remap = false)
public abstract class DistantHorizonsPipelineBuilderWaterMaskMixin {
    @Shadow @Final private RenderPipeline.Builder blazePipelineBuilder;

    @Inject(method = "build", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;build()"
                    + "Lcom/mojang/blaze3d/pipeline/RenderPipeline;", shift = At.Shift.BEFORE), require = 0)
    private void caustica$addWaterMaskTarget(CallbackInfoReturnable<RenderPipeline> cir) {
        DistantHorizonsWaterMask.addColorTarget(blazePipelineBuilder);
    }
}
