package dev.comfyfluffy.caustica.rt.graph;

import java.util.Objects;

/** A producer-to-consumer dependency through one logical resource. */
public record GraphAccess(GraphPass producer, GraphPass consumer, GraphResource resource) {
    public GraphAccess {
        Objects.requireNonNull(producer, "producer");
        Objects.requireNonNull(consumer, "consumer");
        Objects.requireNonNull(resource, "resource");
        if (producer.equals(consumer)) {
            throw new IllegalArgumentException("a graph access cannot depend on its own pass");
        }
    }
}
