package dev.comfyfluffy.caustica.rt.graph;

import java.util.List;
import java.util.Set;

import static dev.comfyfluffy.caustica.rt.graph.GraphResourceUse.Mode.*;
import static dev.comfyfluffy.caustica.rt.graph.GraphResourceUse.Queue.*;
import static dev.comfyfluffy.caustica.rt.graph.GraphResourceUse.Stage.*;
import static dev.comfyfluffy.caustica.rt.graph.GraphResourceUse.Layout.*;

/**
 * Conservative resource families at the five legacy macro-pass boundaries.
 * Optional backend internals and aliases are opaque: these declarations are
 * unsuitable for barrier synthesis without per-image/per-branch refinement.
 */
public final class FrameResourceDeclarations {
    private static final GraphResource FRAME = new GraphResource("frameContext/invocation");
    private static final GraphResource TEMPORAL = new GraphResource("temporalState/fgJitter");
    private static final GraphResource SCENE = new GraphResource("scene/AS/materials/pushConstants");
    private static final GraphResource TRACE_HISTORY = new GraphResource("ReSTIR/SHaRC/tracerHistory");
    private static final GraphResource TRACE = new GraphResource("output/gBuffer/NRD-signals");
    private static final GraphResource NRD = new GraphResource("external-NRD-result");
    private static final GraphResource RECONSTRUCTION = new GraphResource("reconstruction-backend-state");
    private static final GraphResource SELECTED = new GraphResource("selected-upscale-source/result");
    private static final GraphResource UPSCALER = new GraphResource("upscaler-backend-state");
    private static final GraphResource RR = new GraphResource("rrOutput");
    private static final GraphResource EXPOSURE = new GraphResource("exposure-state");
    private static final GraphResource DISPLAY = new GraphResource("displayImage");
    private static final GraphResource TARGET = new GraphResource("main-target");

    public static Set<GraphResource> resources() {
        return Set.of(FRAME, TEMPORAL, SCENE, TRACE_HISTORY, TRACE, NRD, RECONSTRUCTION,
                SELECTED, UPSCALER, RR, EXPOSURE, DISPLAY, TARGET);
    }

    public static Set<GraphResource> imported() {
        // NRD runs between trace and reconstruction, outside FramePipeline's callbacks.
        // Histories are initialized/reset by their legacy owners, not this graph.
        return Set.of(FRAME, SCENE, TRACE_HISTORY, NRD, RECONSTRUCTION, UPSCALER, EXPOSURE);
    }

    private FrameResourceDeclarations() {}

    public static GraphPass pass(String name) {
        List<GraphResourceUse> uses = switch (name) {
            case "PrepareFramePass" -> List.of(host(FRAME, READ), host(TEMPORAL, WRITE));
            case "PathTracePass" -> List.of(host(FRAME, READ),
                    gpu(SCENE, READ, NOT_APPLICABLE, RAY_TRACING),
                    gpu(TRACE_HISTORY, READ_WRITE, NOT_APPLICABLE, RAY_TRACING),
                    gpu(TRACE, WRITE, GENERAL, RAY_TRACING));
            case "ReconstructionPass" -> List.of(host(FRAME, READ),
                    gpu(TRACE, READ, GENERAL, COMPUTE, TRANSFER, GraphResourceUse.Stage.BACKEND_MANAGED),
                    gpu(NRD, READ, GENERAL, COMPUTE, TRANSFER),
                    gpu(RECONSTRUCTION, READ_WRITE, GraphResourceUse.Layout.BACKEND_MANAGED,
                            COMPUTE, TRANSFER, GraphResourceUse.Stage.BACKEND_MANAGED),
                    gpu(RR, WRITE, GENERAL, GraphResourceUse.Stage.BACKEND_MANAGED),
                    host(SELECTED, WRITE));
            case "UpscalePass" -> List.of(host(FRAME, READ), host(SELECTED, READ),
                    gpu(TRACE, READ, GENERAL, COMPUTE, TRANSFER, GraphResourceUse.Stage.BACKEND_MANAGED),
                    gpu(NRD, READ, GENERAL, COMPUTE, TRANSFER, GraphResourceUse.Stage.BACKEND_MANAGED),
                    gpu(RECONSTRUCTION, READ, GraphResourceUse.Layout.BACKEND_MANAGED,
                            COMPUTE, TRANSFER, GraphResourceUse.Stage.BACKEND_MANAGED),
                    gpu(UPSCALER, READ_WRITE, GraphResourceUse.Layout.BACKEND_MANAGED,
                            COMPUTE, TRANSFER, GraphResourceUse.Stage.BACKEND_MANAGED),
                    // This boundary guarantees rrOutput is available: RR has already
                    // produced it, or this callback invokes a temporal/native fallback.
                    gpu(RR, WRITE, GENERAL, COMPUTE, TRANSFER, GraphResourceUse.Stage.BACKEND_MANAGED));
            case "PostPresentPass" -> List.of(gpu(RR, READ, GENERAL, COMPUTE),
                    gpu(EXPOSURE, READ_WRITE, GENERAL, COMPUTE),
                    gpu(DISPLAY, WRITE, GENERAL, COMPUTE),
                    gpu(DISPLAY, READ, GENERAL, TRANSFER),
                    gpu(TARGET, WRITE, GENERAL, TRANSFER));
            default -> throw new IllegalArgumentException("missing resource declarations for " + name);
        };
        return new GraphPass(name, uses);
    }

    private static GraphResourceUse host(GraphResource resource, GraphResourceUse.Mode mode) {
        return new GraphResourceUse(resource, mode, GraphResourceUse.Queue.HOST,
                Set.of(GraphResourceUse.Stage.HOST), NOT_APPLICABLE);
    }

    private static GraphResourceUse gpu(GraphResource resource, GraphResourceUse.Mode mode,
                                        GraphResourceUse.Layout layout, GraphResourceUse.Stage... stages) {
        return new GraphResourceUse(resource, mode, GRAPHICS, Set.of(stages), layout);
    }
}
