package dev.comfyfluffy.caustica.rt.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.comfyfluffy.caustica.rt.graph.BarrierSynthesis.Layout.BUFFER;
import static dev.comfyfluffy.caustica.rt.graph.BarrierSynthesis.Layout.GENERAL;
import static dev.comfyfluffy.caustica.rt.graph.BarrierSynthesis.Mode.READ;
import static dev.comfyfluffy.caustica.rt.graph.BarrierSynthesis.Mode.READ_WRITE;
import static dev.comfyfluffy.caustica.rt.graph.BarrierSynthesis.Mode.WRITE;
import static dev.comfyfluffy.caustica.rt.graph.BarrierSynthesis.Stage.COMPUTE;

/**
 * Per-resource model for the two Caustica path-trace dispatches.
 *
 * <p>Previous ReSTIR and SHaRC state are imported cross-frame inputs. Current
 * ReSTIR state and SHaRC updates are exported for the next frame; all images
 * are current-frame outputs consumed by reconstruction/upscale.</p>
 */
public final class PathTraceBarrierPlan {
    public static final String INDIRECT = "indirect";
    public static final String EXPORT = "export";

    private final List<BarrierSynthesis.Operation> operations;
    private final java.util.Map<String, BarrierSynthesis.Barrier> barriers;

    public static PathTraceBarrierPlan create(boolean viewZ, boolean nrdSignals) {
        List<BarrierSynthesis.Use> primary = new ArrayList<>();
        primary.add(buffer("continuationQueue", WRITE));
        primary.add(image("color", WRITE));
        primary.add(image("normal", WRITE));
        primary.add(image("albedo", WRITE));
        primary.add(image("depth", WRITE));
        primary.add(image("motion", WRITE));
        primary.add(image("specularAlbedo", WRITE));
        primary.add(image("specularMotion", WRITE));
        if (viewZ) primary.add(image("viewZ", WRITE));

        List<BarrierSynthesis.Use> indirect = new ArrayList<>();
        indirect.add(buffer("continuationQueue", READ));
        indirect.add(image("color", WRITE));
        indirect.add(buffer("restirPrevious", READ));
        indirect.add(buffer("restirCurrent", WRITE));
        indirect.add(buffer("sharcState", READ_WRITE));
        if (nrdSignals) {
            indirect.add(image("normal", READ));
            indirect.add(image("albedo", READ));
            indirect.add(image("specularAlbedo", READ));
            indirect.add(image("viewZ", READ));
            indirect.add(image("nrdDiffuse", WRITE));
            indirect.add(image("nrdSpecular", WRITE));
        }

        List<BarrierSynthesis.Use> export = new ArrayList<>();
        export.add(image("color", READ));
        export.add(image("normal", READ));
        export.add(image("albedo", READ));
        export.add(image("depth", READ));
        export.add(image("motion", READ));
        export.add(image("specularAlbedo", READ));
        export.add(image("specularMotion", READ));
        if (viewZ) export.add(image("viewZ", READ));
        if (nrdSignals) {
            export.add(image("nrdDiffuse", READ));
            export.add(image("nrdSpecular", READ));
        }
        export.add(buffer("restirCurrent", READ));
        export.add(buffer("sharcState", READ));

        return new PathTraceBarrierPlan(List.of(
                new BarrierSynthesis.Operation("primary", primary),
                new BarrierSynthesis.Operation(INDIRECT, indirect),
                new BarrierSynthesis.Operation(EXPORT, export)),
                Set.of("restirPrevious", "sharcState"));
    }

    public List<BarrierSynthesis.Operation> operations() {
        return operations;
    }

    public BarrierSynthesis.Barrier before(String operation) {
        BarrierSynthesis.Barrier result = barriers.get(operation);
        if (result == null) throw new IllegalArgumentException("undeclared path-trace operation: " + operation);
        return result;
    }

    private PathTraceBarrierPlan(List<BarrierSynthesis.Operation> operations, Set<String> imported) {
        this.operations = List.copyOf(operations);
        barriers = BarrierSynthesis.compile(this.operations, imported).stream()
                .collect(Collectors.toUnmodifiableMap(BarrierSynthesis.Barrier::beforeOperation, barrier -> barrier));
    }

    private static BarrierSynthesis.Use image(String name, BarrierSynthesis.Mode mode) {
        return new BarrierSynthesis.Use(name, mode, COMPUTE, GENERAL);
    }

    private static BarrierSynthesis.Use buffer(String name, BarrierSynthesis.Mode mode) {
        return new BarrierSynthesis.Use(name, mode, COMPUTE, BUFFER);
    }
}
