package dev.comfyfluffy.caustica.rt.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static dev.comfyfluffy.caustica.rt.graph.BarrierSynthesis.Mode.*;
import static dev.comfyfluffy.caustica.rt.graph.BarrierSynthesis.Stage.*;
import static dev.comfyfluffy.caustica.rt.graph.BarrierSynthesis.Layout.*;

/** Precise logical resource identities; global barriers need no image-handle binding. */
public final class PostBarrierPlan {
    public static final String HISTOGRAM = "histogram";
    public static final String RESOLVE = "resolve";
    public static final String DISPLAY = "display";
    public static final String COPY = "copy";
    public static final String EXPORT = "export";
    private static final PostBarrierPlan[] PLANS = {
            new PostBarrierPlan(false, false), new PostBarrierPlan(false, true),
            new PostBarrierPlan(true, false), new PostBarrierPlan(true, true)};
    private final boolean auto;
    private final List<BarrierSynthesis.Operation> operations;
    private final Map<String, BarrierSynthesis.Barrier> barriers;

    public static PostBarrierPlan of(boolean auto, boolean hdr) { return PLANS[(auto ? 2 : 0) + (hdr ? 1 : 0)]; }
    public boolean automaticExposure() { return auto; }
    public List<BarrierSynthesis.Operation> operations() { return operations; }
    public BarrierSynthesis.Barrier before(String operation) {
        var barrier = barriers.get(operation);
        if (barrier == null) throw new IllegalArgumentException("undeclared post operation: " + operation);
        return barrier;
    }

    private PostBarrierPlan(boolean auto, boolean hdr) {
        this.auto = auto;
        List<BarrierSynthesis.Operation> ops = new ArrayList<>();
        if (auto) {
            ops.add(op("clearHistogram", buffer("histogramBuffer", WRITE, TRANSFER)));
            ops.add(op(HISTOGRAM, image("rrOutput", READ, COMPUTE), buffer("histogramBuffer", READ_WRITE, COMPUTE)));
            ops.add(op(RESOLVE, buffer("histogramBuffer", READ, COMPUTE), buffer("exposureState", READ_WRITE, COMPUTE),
                    image("exposureImage", WRITE, COMPUTE)));
        } else {
            ops.add(op("manualExposure", image("exposureImage", WRITE, TRANSFER)));
        }
        List<BarrierSynthesis.Use> display = new ArrayList<>(List.of(image("rrOutput", READ, COMPUTE),
                image("exposureImage", READ, COMPUTE), image("displayImage", WRITE, COMPUTE)));
        if (hdr) display.add(image("hdrDisplayImage", WRITE, COMPUTE));
        ops.add(new BarrierSynthesis.Operation(DISPLAY, display));
        ops.add(op(COPY, image("displayImage", READ, TRANSFER), image("mainTarget", WRITE, TRANSFER)));
        List<BarrierSynthesis.Use> exported = new ArrayList<>(List.of(image("mainTarget", READ_WRITE, EXTERNAL),
                image("displayImage", READ_WRITE, EXTERNAL), image("exposureImage", READ_WRITE, EXTERNAL)));
        if (hdr) exported.add(image("hdrDisplayImage", READ_WRITE, EXTERNAL));
        // The final broad barrier preserves visibility to opaque later consumers.
        ops.add(new BarrierSynthesis.Operation(EXPORT, exported));
        operations = List.copyOf(ops);
        barriers = BarrierSynthesis.compile(operations, Set.of("rrOutput", "exposureState")).stream()
                .collect(Collectors.toUnmodifiableMap(BarrierSynthesis.Barrier::beforeOperation, b -> b));
    }

    private static BarrierSynthesis.Operation op(String name, BarrierSynthesis.Use... uses) {
        return new BarrierSynthesis.Operation(name, List.of(uses));
    }
    private static BarrierSynthesis.Use image(String id, BarrierSynthesis.Mode mode, BarrierSynthesis.Stage stage) {
        return new BarrierSynthesis.Use(id, mode, stage, GENERAL);
    }
    private static BarrierSynthesis.Use buffer(String id, BarrierSynthesis.Mode mode, BarrierSynthesis.Stage stage) {
        return new BarrierSynthesis.Use(id, mode, stage, BUFFER);
    }
}
