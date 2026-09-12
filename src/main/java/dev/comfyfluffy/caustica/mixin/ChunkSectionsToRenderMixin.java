package dev.comfyfluffy.caustica.mixin;

import com.mojang.blaze3d.textures.GpuSampler;
import dev.comfyfluffy.caustica.client.VanillaRenderController;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps LevelRenderer orchestration alive while removing only vanilla chunk geometry already owned by RT.
 *
 * <p>Distant Horizons 3.2.0 injects into this same method at HEAD with {@code order=800}. This callback
 * deliberately runs later ({@code order=1000}), so DH performs its native OPAQUE/deferred render hook first;
 * cancellation then skips only the body of Minecraft's chunk-group draw.</p>
 */
@Mixin(ChunkSectionsToRender.class)
public abstract class ChunkSectionsToRenderMixin {
	@Inject(method = "renderGroup", at = @At("HEAD"), cancellable = true, order = 1000)
	private void caustica$suppressVanillaTerrain(ChunkSectionLayerGroup group, GpuSampler sampler,
			CallbackInfo ci) {
		if (group != ChunkSectionLayerGroup.OPAQUE && group != ChunkSectionLayerGroup.TRANSLUCENT) {
			return;
		}
		if (VanillaRenderController.INSTANCE.shouldSuppressVanillaTerrain()) {
			VanillaRenderController.INSTANCE.markTerrainGroupSuppressed(group);
			ci.cancel();
		}
	}
}
