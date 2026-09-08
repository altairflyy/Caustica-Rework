package dev.comfyfluffy.caustica.rt.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Single-queue, fixed-layout hazard compiler. No ownership transfer or aliasing. */
public final class BarrierSynthesis {
    public enum Mode { READ, WRITE, READ_WRITE }
    public enum Stage { COMPUTE, TRANSFER, EXTERNAL }
    public enum Layout { GENERAL, BUFFER }
    public record Use(String resource, Mode mode, Stage stage, Layout layout) {
        public Use {
            if (resource == null || resource.isBlank() || mode == null || stage == null || layout == null)
                throw new IllegalArgumentException("incomplete resource use");
        }
    }
    public record Operation(String name, List<Use> uses) {
        public Operation {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("missing operation name");
            uses = List.copyOf(uses);
        }
    }
    public record Hazard(String resource, Use before, Use after) {}
    public record Barrier(String beforeOperation, List<Hazard> hazards) {
        public Barrier { hazards = List.copyOf(hazards); }
        public boolean required() { return !hazards.isEmpty(); }
    }

    private BarrierSynthesis() {}

    public static List<Barrier> compile(List<Operation> operations, Set<String> imported) {
        Map<String, Use> last = new HashMap<>();
        Set<String> names = new java.util.HashSet<>();
        List<Barrier> result = new ArrayList<>();
        for (Operation operation : operations) {
            if (!names.add(operation.name())) throw new IllegalArgumentException("duplicate operation");
            Set<String> resources = new java.util.HashSet<>();
            List<Hazard> hazards = new ArrayList<>();
            for (Use use : operation.uses()) {
                if (!resources.add(use.resource())) throw new IllegalArgumentException("duplicate resource use");
                Use previous = last.get(use.resource());
                if (previous == null && use.mode() != Mode.WRITE && !imported.contains(use.resource()))
                    throw new IllegalArgumentException("read before write: " + use.resource());
                if (previous != null) {
                    if (previous.layout() != use.layout())
                        throw new IllegalArgumentException("layout transition outside fixed-layout scope");
                    if (previous.mode() != Mode.READ || use.mode() != Mode.READ)
                        hazards.add(new Hazard(use.resource(), previous, use));
                }
                last.put(use.resource(), use);
            }
            result.add(new Barrier(operation.name(), hazards));
        }
        return List.copyOf(result);
    }
}
