package dev.comfyfluffy.caustica.rt.frame;

import java.util.List;
import java.util.Objects;

/**
 * Immutable ordered callback bindings consumed by the frame graph.
 * Execution belongs exclusively to GraphExecution; this container owns no resources.
 */
public final class FramePipeline {
    private final List<FramePass> passes;

    public FramePipeline(FramePass... passes) {
        Objects.requireNonNull(passes, "passes");
        this.passes = List.of(passes.clone());
        if (this.passes.isEmpty()) {
            throw new IllegalArgumentException("a frame pipeline requires at least one pass");
        }
        this.passes.forEach(pass -> Objects.requireNonNull(pass, "pass"));
    }

    public int passCount() {
        return passes.size();
    }

    /** Immutable callback bindings shared with the graph's compiled execution order. */
    public List<FramePass> passes() {
        return passes;
    }

    /** Stable names used to bind and validate the frame graph. */
    public List<String> declaredPassNames() {
        return passes.stream().map(pass -> pass.getClass().getSimpleName()).toList();
    }

}
