package dev.comfyfluffy.caustica.rewrite;

/**
 * Development-only switches used while migrating the renderer incrementally.
 *
 * <p>These gates deliberately live outside {@code CausticaConfig}: they are not player settings,
 * are not serialized, and are not exposed in the options UI. Each gate defaults to {@code false}
 * so adding the gate itself cannot alter the frozen-reference runtime path. A migration task may
 * wire one gate to a new implementation only when that task explicitly requires an A/B path.
 */
public final class RewriteGates {
    public static final String FRAME_CONTEXT_V2_KEY = "engine.frameContextV2";
    public static final String TEMPORAL_V2_KEY = "engine.temporalV2";
    public static final String LOD_V2_KEY = "engine.lodV2";
    public static final String PIPELINE_V2_KEY = "engine.pipelineV2";
    public static final String GPU_OWNERSHIP_V2_KEY = "engine.gpuOwnershipV2";
    public static final String SCENE_V2_KEY = "engine.sceneV2";
    public static final String RENDER_GRAPH_V2_KEY = "engine.renderGraphV2";
    public static final String POST_BARRIERS_V2_KEY = "engine.postBarriersV2";
    public static final String DENOISER_BARRIERS_V2_KEY = "engine.denoiserBarriersV2";

    private RewriteGates() {
    }

    public static boolean frameContextV2() {
        return enabled(FRAME_CONTEXT_V2_KEY);
    }

    public static boolean temporalV2() {
        return enabled(TEMPORAL_V2_KEY);
    }

    public static boolean lodV2() {
        return enabled(LOD_V2_KEY);
    }

    public static boolean pipelineV2() {
        return enabled(PIPELINE_V2_KEY);
    }

    public static boolean gpuOwnershipV2() {
        return enabled(GPU_OWNERSHIP_V2_KEY);
    }

    public static boolean sceneV2() {
        return enabled(SCENE_V2_KEY);
    }

    public static boolean renderGraphV2() {
        return enabled(RENDER_GRAPH_V2_KEY);
    }

    public static boolean postBarriersV2() {
        return renderGraphV2() && enabled(POST_BARRIERS_V2_KEY);
    }

    public static boolean denoiserBarriersV2() {
        return renderGraphV2() && enabled(DENOISER_BARRIERS_V2_KEY);
    }

    private static boolean enabled(String key) {
        return Boolean.parseBoolean(System.getProperty(key, "false"));
    }
}
