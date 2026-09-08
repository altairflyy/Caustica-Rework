package dev.comfyfluffy.caustica.rt.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.comfyfluffy.caustica.rt.graph.BarrierSynthesis.Layout.GENERAL;
import static dev.comfyfluffy.caustica.rt.graph.BarrierSynthesis.Mode.*;
import static dev.comfyfluffy.caustica.rt.graph.BarrierSynthesis.Stage.*;

/** Per-image/per-operation plans for SVGF and the external NRD combine seam. */
public final class DenoiserBarrierPlan {
    public static final String REPROJECT = "reproject";
    public static final String HISTORY_FEEDBACK = "historyFeedback";
    public static final String PREVIOUS_GUIDES = "previousGuides";
    public static final String EXPORT = "export";
    public static final String NRD_COMBINE = "nrdCombine";
    public static final String NRD_EXPORT = "nrdExport";

    private final List<BarrierSynthesis.Operation> operations;
    private final Map<String, BarrierSynthesis.Barrier> barriers;

    public static DenoiserBarrierPlan svgf(boolean writeToPing, int atrousPasses, int feedbackPass) {
        if (atrousPasses <= 0 || feedbackPass < 0 || feedbackPass >= atrousPasses)
            throw new IllegalArgumentException("invalid SVGF pass configuration");
        String historyIn = writeToPing ? "historyPong" : "historyPing";
        String historyOut = writeToPing ? "historyPing" : "historyPong";
        String momentsIn = writeToPing ? "momentsPong" : "momentsPing";
        String momentsOut = writeToPing ? "momentsPing" : "momentsPong";
        List<BarrierSynthesis.Operation> ops = new ArrayList<>();
        ops.add(op("inputsReady", writes("currentSource", "motion", "currentViewZ", "currentNormal", "albedo")));
        ops.add(op(REPROJECT, concat(reads("currentSource", "motion", "currentViewZ", "currentNormal", "albedo",
                historyIn, momentsIn, "previousViewZ", "previousNormal"), writes(historyOut, momentsOut, "filterPing"))));
        String source = "filterPing", destination = "filterPong";
        for (int pass = 0; pass < atrousPasses; pass++) {
            String operation = atrous(pass);
            ops.add(op(operation, concat(reads(source, "currentViewZ", "currentNormal", momentsOut, "albedo"),
                    writes(destination))));
            if (pass == feedbackPass) {
                ops.add(op(HISTORY_FEEDBACK, concat(reads(destination), writes(historyOut))));
            }
            String swap = source; source = destination; destination = swap;
        }
        ops.add(op(PREVIOUS_GUIDES, concat(reads("currentViewZ", "currentNormal"),
                writes("previousViewZ", "previousNormal"))));
        ops.add(op(EXPORT, reads(source, historyOut, momentsOut, "previousViewZ", "previousNormal")));
        return new DenoiserBarrierPlan(ops, Set.of(historyIn, momentsIn, "previousViewZ", "previousNormal"));
    }

    public static DenoiserBarrierPlan nrd() {
        return new DenoiserBarrierPlan(List.of(
                op("externalNrd", writes("nrdDiffuse", "nrdSpecular", "nrdValidation")),
                op(NRD_COMBINE, concat(reads("nrdDiffuse", "nrdSpecular"), writes("nrdCombined"))),
                op(NRD_EXPORT, reads("nrdCombined"))), Set.of());
    }

    public static String atrous(int pass) { return "atrous" + pass; }
    public List<BarrierSynthesis.Operation> operations() { return operations; }
    public BarrierSynthesis.Barrier before(String operation) {
        BarrierSynthesis.Barrier result = barriers.get(operation);
        if (result == null) throw new IllegalArgumentException("undeclared denoiser operation: " + operation);
        return result;
    }

    private DenoiserBarrierPlan(List<BarrierSynthesis.Operation> operations, Set<String> imported) {
        this.operations = List.copyOf(operations);
        barriers = BarrierSynthesis.compile(this.operations, imported).stream()
                .collect(Collectors.toUnmodifiableMap(BarrierSynthesis.Barrier::beforeOperation, b -> b));
    }

    private static BarrierSynthesis.Operation op(String name, List<BarrierSynthesis.Use> uses) {
        return new BarrierSynthesis.Operation(name, uses);
    }
    private static List<BarrierSynthesis.Use> reads(String... names) { return uses(READ, names); }
    private static List<BarrierSynthesis.Use> writes(String... names) { return uses(WRITE, names); }
    private static List<BarrierSynthesis.Use> uses(BarrierSynthesis.Mode mode, String... names) {
        return java.util.Arrays.stream(names).map(name -> new BarrierSynthesis.Use(name, mode, COMPUTE, GENERAL)).toList();
    }
    @SafeVarargs private static List<BarrierSynthesis.Use> concat(List<BarrierSynthesis.Use>... groups) {
        return java.util.Arrays.stream(groups).flatMap(List::stream).toList();
    }
}
