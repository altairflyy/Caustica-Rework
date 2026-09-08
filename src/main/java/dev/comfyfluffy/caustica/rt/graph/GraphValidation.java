package dev.comfyfluffy.caustica.rt.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static dev.comfyfluffy.caustica.rt.graph.GraphResourceUse.Mode.READ;
import static dev.comfyfluffy.caustica.rt.graph.GraphResourceUse.Mode.WRITE;

/** Checks declared dependencies, never the arbitrary order of a topological tie. */
public final class GraphValidation {
    public enum Kind { READ_BEFORE_WRITE, MULTIPLE_WRITER_AMBIGUITY, UNDECLARED_RESOURCE }
    public record Diagnostic(Kind kind, String pass, String resource) {}

    private GraphValidation() {}

    public static List<Diagnostic> diagnose(List<GraphPass> passes, List<GraphAccess> edges,
                                            Set<GraphResource> declared, Set<GraphResource> imported) {
        if (!declared.containsAll(imported)) {
            throw new IllegalArgumentException("imported resources must be declared");
        }
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (GraphPass pass : passes) {
            Set<GraphResource> locallyWritten = new HashSet<>();
            for (GraphResourceUse use : pass.resources()) {
                if (!declared.contains(use.resource())) {
                    diagnostics.add(new Diagnostic(Kind.UNDECLARED_RESOURCE, pass.name(), use.resource().name()));
                    continue;
                }
                if (use.mode() != WRITE && !imported.contains(use.resource())
                        && !locallyWritten.contains(use.resource())
                        && passes.stream().noneMatch(writer -> writes(writer, use.resource())
                        && precedes(writer, pass, edges))) {
                    diagnostics.add(new Diagnostic(Kind.READ_BEFORE_WRITE, pass.name(), use.resource().name()));
                }
                if (use.mode() != READ) locallyWritten.add(use.resource());
            }
        }
        for (GraphResource resource : declared) {
            List<GraphPass> writers = passes.stream().filter(pass -> writes(pass, resource)).toList();
            for (int i = 0; i < writers.size(); i++) {
                for (int j = i + 1; j < writers.size(); j++) {
                    GraphPass a = writers.get(i), b = writers.get(j);
                    if (!precedes(a, b, edges) && !precedes(b, a, edges)) {
                        diagnostics.add(new Diagnostic(Kind.MULTIPLE_WRITER_AMBIGUITY,
                                a.name() + "/" + b.name(), resource.name()));
                    }
                }
            }
        }
        return List.copyOf(diagnostics);
    }

    private static boolean writes(GraphPass pass, GraphResource resource) {
        return pass.resources().stream().anyMatch(use -> use.resource().equals(resource) && use.mode() != READ);
    }

    private static boolean precedes(GraphPass from, GraphPass to, List<GraphAccess> edges) {
        if (from.equals(to)) return false;
        Set<GraphPass> reached = new HashSet<>();
        List<GraphPass> pending = new ArrayList<>();
        pending.add(from);
        for (int i = 0; i < pending.size(); i++) {
            GraphPass current = pending.get(i);
            if (!reached.add(current)) continue;
            for (GraphAccess edge : edges) {
                if (edge.producer().equals(current)) {
                    if (edge.consumer().equals(to)) return true;
                    pending.add(edge.consumer());
                }
            }
        }
        return false;
    }
}
