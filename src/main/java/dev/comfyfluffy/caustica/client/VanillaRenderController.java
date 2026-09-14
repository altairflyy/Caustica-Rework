package dev.comfyfluffy.caustica.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.comfyfluffy.caustica.CausticaMod;
import dev.comfyfluffy.caustica.compat.DistantHorizonsCompat;
import dev.comfyfluffy.caustica.rt.RtComposite;
import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.RtFrameStats;
import dev.comfyfluffy.caustica.rt.terrain.RtTerrain;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;

/** Coordinates the selective vanilla-terrain fallback around the RT composite. */
public final class VanillaRenderController {
	public static final VanillaRenderController INSTANCE = new VanillaRenderController();

	private boolean frameStarted;
	private boolean baseReady;
	private boolean projectionCaptured;
	private boolean levelRendererObserved;
	private boolean terrainSuppressed;
	private boolean nativeDhHookObserved;
	private boolean failureLatched;
	private boolean loggedActive;
	private boolean rtActive = true;
	private Boolean lastLoggedRtActive;
	private String inactiveReason;
	private String lastLoggedInactiveReason;
	private String lastLoggedHybridState;

	private VanillaRenderController() {
	}

	public void beginFrame(RenderTarget mainTarget) {
		this.frameStarted = true;
		this.projectionCaptured = false;
		this.levelRendererObserved = false;
		this.terrainSuppressed = false;
		this.nativeDhHookObserved = false;
		this.baseReady = false;
		this.inactiveReason = null;
		this.rtActive = RtComposite.enabled();

		if (!Boolean.valueOf(this.rtActive).equals(this.lastLoggedRtActive)) {
			this.lastLoggedRtActive = this.rtActive;
			CausticaMod.LOGGER.info("RT output mode: {}", this.rtActive ? "rt" : "vanilla");
		}

		if (!this.rtActive) {
			return;
		}

		this.inactiveReason = findInactiveReason(mainTarget);
		this.baseReady = this.inactiveReason == null;
		if (this.baseReady) {
			if (!this.loggedActive) {
				this.loggedActive = true;
				CausticaMod.LOGGER.info("Selective vanilla terrain suppression active; LevelRenderer orchestration preserved");
			}
		} else {
			logInactive(this.inactiveReason);
		}
	}

	public void markProjectionCaptured() {
		this.projectionCaptured = true;
	}

	public void markLevelRendererObserved() {
		this.levelRendererObserved = true;
	}

	public boolean shouldSuppressVanillaTerrain() {
		if (!this.rtActive) {
			return false;
		}
		if (!this.frameStarted) {
			logInactive("frame controller was not started");
			return false;
		}
		if (!this.baseReady) {
			return false;
		}
		if (!this.projectionCaptured) {
			logInactive("level projection was not captured");
			return false;
		}
		if (!this.levelRendererObserved) {
			logInactive("LevelRenderer orchestration was not observed");
			return false;
		}
		return true;
	}

	public void markTerrainGroupSuppressed(ChunkSectionLayerGroup group) {
		this.terrainSuppressed = true;
		RtFrameStats.FRAME.count("suppressedVanillaTerrainLayerCalls", 1);
		logHybridState();
	}

	/** Called only from DH's real terrain-render entry point; never infer execution from mod presence. */
	public void markNativeDhHookObserved() {
		this.nativeDhHookObserved = true;
		RtFrameStats.FRAME.count("nativeDhHookExecutions", 1);
	}

	public boolean wasNativeDhHookObservedThisFrame() {
		return this.nativeDhHookObserved;
	}

	public boolean wasTerrainSuppressedThisFrame() {
		return this.terrainSuppressed;
	}

	public boolean shouldCompositeRt() {
		return this.rtActive;
	}

	/** Runtime work switch for per-frame RT work; mirrors {@link RtComposite#enabled()}. */
	public static boolean rtRuntimeWorkRequested() {
		return RtComposite.enabled();
	}

	public void markRtCompositeResult(boolean success) {
		if (this.terrainSuppressed && !success) {
			latchFailure("RT composite did not produce a replacement frame");
		}
	}

	public void markMissedBeforeHandSeam() {
		if (this.terrainSuppressed) {
			latchFailure("missed before-hand RT composite seam after vanilla terrain was suppressed");
		}
	}

	private void logHybridState() {
		String state = "levelRendererCancelled=false"
				+ " vanillaTerrainSuppressed=" + this.terrainSuppressed
				+ " nativeDhHookObserved=" + this.nativeDhHookObserved
				+ " manualDhRender=false"
				+ " rtRing=" + (DistantHorizonsCompat.dhRtRingEnabled() ? "ON" : "OFF")
				+ " mode=CANONICAL_RT_NEAR_AND_DH_FAR";
		if (!state.equals(this.lastLoggedHybridState)) {
			this.lastLoggedHybridState = state;
			CausticaMod.LOGGER.info("[Caustica DH Hybrid] {}", state);
		}
	}

	private String findInactiveReason(RenderTarget mainTarget) {
		if (this.failureLatched || RtComposite.INSTANCE.hasFailed()) {
			return "RT composite failure latch is set";
		}
		if (!RtComposite.enabled()) {
			return "caustica.rt is false";
		}
		if (RtContext.currentOrNull() == null) {
			return "RT context is not ready";
		}
		if (RtTerrain.currentOrNull() == null) {
			return "RT terrain is not ready";
		}
		if (RtComposite.INSTANCE.requiresVanillaWorldFallback()) {
			return "RT resources are crossing an epoch boundary";
		}
		if (mainTarget == null || mainTarget.getColorTexture() == null || mainTarget.getDepthTexture() == null) {
			return "main render target textures are not ready";
		}
		return null;
	}

	private void latchFailure(String reason) {
		if (!this.failureLatched) {
			CausticaMod.LOGGER.warn("Disabling vanilla terrain suppression: {}", reason);
		}
		this.failureLatched = true;
		this.baseReady = false;
		this.inactiveReason = reason;
	}

	private void logInactive(String reason) {
		if (reason == null || reason.equals(this.lastLoggedInactiveReason)) {
			return;
		}
		CausticaMod.LOGGER.info("Vanilla terrain suppression inactive: {}", reason);
		this.lastLoggedInactiveReason = reason;
	}
}
