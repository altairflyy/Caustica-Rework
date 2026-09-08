package dev.comfyfluffy.caustica.rt.graph;

import dev.comfyfluffy.caustica.rt.frame.FramePipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Non-executing graph description of the current linear frame pipeline.
 * GPU execution and resource ownership remain entirely in the legacy pipeline.
 */
public final class FrameGraph {
    private final List<GraphPass> passes;
    private final List<GraphAccess> accesses;
    private final List<GraphPass> topologicalOrder;

    private FrameGraph(List<GraphPass> passes, List<GraphAccess> accesses) {
        this.passes = List.copyOf(passes);
        this.accesses = List.copyOf(accesses);
        this.topologicalOrder = computeTopologicalOrder();
    }

    public static FrameGraph shadow(FramePipeline pipeline) {
        Objects.requireNonNull(pipeline, "pipeline");
        List<GraphPass> passes = pipeline.declaredPassNames().stream().map(GraphPass::new).toList();
        List<GraphAccess> accesses = new ArrayList<>();
        for (int i = 1; i < passes.size(); i++) {
            GraphPass producer = passes.get(i - 1);
            GraphPass consumer = passes.get(i);
            accesses.add(new GraphAccess(producer, consumer,
                    new GraphResource(producer.name() + "->" + consumer.name())));
        }
        FrameGraph graph = new FrameGraph(passes, accesses);
        if (!graph.topologicalOrderNames().equals(pipeline.declaredPassNames())) {
            throw new IllegalStateException("shadow graph order differs from the frame pipeline");
        }
        return graph;
    }

    public List<GraphPass> passes() {
        return passes;
    }

    public List<GraphAccess> accesses() {
        return accesses;
    }

    public List<GraphPass> topologicalOrder() {
        return topologicalOrder;
    }

    public List<String> topologicalOrderNames() {
        return topologicalOrder.stream().map(GraphPass::name).toList();
    }

    private List<GraphPass> computeTopologicalOrder() {
        Map<GraphPass, Integer> incoming = new LinkedHashMap<>();
        passes.forEach(pass -> incoming.put(pass, 0));
        for (GraphAccess access : accesses) {
            if (!incoming.containsKey(access.producer()) || !incoming.containsKey(access.consumer())) {
                throw new IllegalArgumentException("graph access references an undeclared pass");
            }
            incoming.compute(access.consumer(), (pass, count) -> count + 1);
        }

        List<GraphPass> order = new ArrayList<>();
        while (order.size() < passes.size()) {
            GraphPass next = passes.stream()
                    .filter(pass -> incoming.get(pass) == 0 && !order.contains(pass))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("shadow graph contains a cycle"));
            order.add(next);
            accesses.stream().filter(access -> access.producer().equals(next))
                    .forEach(access -> incoming.compute(access.consumer(), (pass, count) -> count - 1));
        }
        return List.copyOf(order);
    }
}
