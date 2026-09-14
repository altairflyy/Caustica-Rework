package dev.comfyfluffy.caustica.mixin;

import dev.comfyfluffy.caustica.client.VanillaRenderController;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	/**
	 * Observation seam only. LevelRenderer must run so its frame graph, entity/world stages and Distant
	 * Horizons' native renderGroup hook retain their normal execution context. Ordinary chunk terrain is
	 * suppressed later by {@link ChunkSectionsToRenderMixin}.
	 */
	@Inject(method = "render", at = @At("HEAD"))
	private void caustica$observeLevelRenderer(CallbackInfo ci) {
		VanillaRenderController.INSTANCE.markLevelRendererObserved();
	}
}
