package dev.comfyfluffy.caustica.mixin;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.comfyfluffy.caustica.compat.DistantHorizonsWaterMask;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.render.blaze.wrappers.RenderPassWrapper", remap = false)
public abstract class DistantHorizonsRenderPassWaterMaskMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/systems/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;"
                    + "Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/Optional;"
                    + "Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)"
                    + "Lcom/mojang/blaze3d/systems/RenderPass;"), require = 0)
    private RenderPass caustica$attachWaterMask(CommandEncoder encoder, Supplier<String> label,
                                                GpuTextureView color, Optional<Vector4fc> colorClear,
                                                GpuTextureView depth, OptionalDouble depthClear) {
        return DistantHorizonsWaterMask.createRenderPass(
                encoder, label, color, colorClear, depth, depthClear);
    }
}
