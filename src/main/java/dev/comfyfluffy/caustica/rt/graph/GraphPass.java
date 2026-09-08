package dev.comfyfluffy.caustica.rt.graph;

import java.util.List;

/** A named pass in the non-executing shadow graph. */
public record GraphPass(String name, List<GraphResourceUse> resources) {
    public GraphPass(String name) {
        this(name, List.of());
    }

    public GraphPass {
        resources = List.copyOf(resources);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("graph pass name must not be blank");
        }
    }
}
