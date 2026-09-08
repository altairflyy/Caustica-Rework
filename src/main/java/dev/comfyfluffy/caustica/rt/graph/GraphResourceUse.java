package dev.comfyfluffy.caustica.rt.graph;

import java.util.Objects;
import java.util.Set;

/** Descriptive macro-pass access; never used to emit barriers or bind GPU objects. */
public record GraphResourceUse(GraphResource resource, Mode mode, Queue queue,
                               Set<Stage> stages, Layout layout) {
    public enum Mode { READ, WRITE, READ_WRITE }
    public enum Queue { HOST, GRAPHICS }
    public enum Stage { HOST, RAY_TRACING, COMPUTE, TRANSFER, BACKEND_MANAGED }
    public enum Layout { NOT_APPLICABLE, GENERAL, BACKEND_MANAGED }

    public GraphResourceUse {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(queue, "queue");
        stages = Set.copyOf(stages);
        if (stages.isEmpty()) throw new IllegalArgumentException("stages must not be empty");
        Objects.requireNonNull(layout, "layout");
    }
}
