package dev.comfyfluffy.caustica.mixin;

import dev.comfyfluffy.caustica.compat.DistantHorizonsWaterMask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks only DH's native terrain pipelines/pass; native LevelRenderer orchestration is untouched. */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.render.blaze.BlazeDhTerrainRenderer", remap = false)
public abstract class DistantHorizonsTerrainWaterMaskMixin {
    private static final String VERTEX_SHADER_CALL =
            "Lcom/seibel/distanthorizons/common/render/blaze/wrappers/RenderPipelineBuilderWrapper;"
                    + "withVertexShader(Ljava/lang/String;)"
                    + "Lcom/seibel/distanthorizons/common/render/blaze/wrappers/RenderPipelineBuilderWrapper;";
    private static final String FRAGMENT_SHADER_CALL =
            "Lcom/seibel/distanthorizons/common/render/blaze/wrappers/RenderPipelineBuilderWrapper;"
                    + "withFragmentShader(Ljava/lang/String;)"
                    + "Lcom/seibel/distanthorizons/common/render/blaze/wrappers/RenderPipelineBuilderWrapper;";

    @Inject(method = "tryInit", at = @At("HEAD"), require = 0)
    private void caustica$beginPipelineBuild(CallbackInfo ci) {
        DistantHorizonsWaterMask.beginTerrainPipelineBuild();
    }

    @Inject(method = "tryInit", at = @At("RETURN"), require = 0)
    private void caustica$endPipelineBuild(CallbackInfo ci) {
        DistantHorizonsWaterMask.endTerrainPipelineBuild();
    }

    @ModifyArg(method = "tryInit", at = @At(value = "INVOKE", target = VERTEX_SHADER_CALL),
            index = 0, require = 0)
    private String caustica$waterMaskVertexShader(String original) {
        return "terrain/caustica_water_mask/vert";
    }

    @ModifyArg(method = "tryInit", at = @At(value = "INVOKE", target = FRAGMENT_SHADER_CALL),
            index = 0, require = 0)
    private String caustica$waterMaskFragmentShader(String original) {
        return "terrain/caustica_water_mask/frag";
    }

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void caustica$beginTerrainPass(@Coerce Object params, boolean opaque,
                                           @Coerce Object lodBuffers, @Coerce Object profiler,
                                           CallbackInfo ci) {
        DistantHorizonsWaterMask.beginTerrainPass(opaque);
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void caustica$endTerrainPass(@Coerce Object params, boolean opaque,
                                         @Coerce Object lodBuffers, @Coerce Object profiler,
                                         CallbackInfo ci) {
        DistantHorizonsWaterMask.endTerrainPass();
    }
}
