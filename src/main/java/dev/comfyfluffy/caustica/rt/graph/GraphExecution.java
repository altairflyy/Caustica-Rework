package dev.comfyfluffy.caustica.rt.graph;

import dev.comfyfluffy.caustica.rt.frame.FrameContext;
import dev.comfyfluffy.caustica.rt.frame.FrameCursor;
import dev.comfyfluffy.caustica.rt.frame.FramePass;
import dev.comfyfluffy.caustica.rt.frame.FramePipeline;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/** Executes callbacks exclusively in the validated frame-graph order. */
public final class GraphExecution {
    private final List<FramePass> ordered;

    public GraphExecution(FrameGraph graph, FramePipeline executable) {
        Objects.requireNonNull(executable, "executable");
        var bindings = new HashMap<String, FramePass>();
        for (FramePass pass : executable.passes()) {
            String name = pass.getClass().getSimpleName();
            if (bindings.put(name, pass) != null) {
                throw new IllegalArgumentException("duplicate pass binding: " + name);
            }
        }
        if (graph.passes().size() != bindings.size() || !graph.diagnostics().isEmpty()) {
            throw new IllegalArgumentException("graph does not match executable pipeline");
        }
        ordered = graph.topologicalOrder().stream().map(pass ->
                Objects.requireNonNull(bindings.get(pass.name()), "missing binding: " + pass.name())).toList();
    }

    /** Begin one graph-authoritative frame execution. */
    public FrameCursor begin(FrameContext frame) {
        Objects.requireNonNull(frame, "frame");
        return new Cursor(frame);
    }

    private final class Cursor implements FrameCursor {
        private final FrameContext frame;
        private int next;

        private Cursor(FrameContext frame) {
            this.frame = frame;
        }

        @Override
        public void executeNext() {
            if (complete()) throw new IllegalStateException("frame graph is already complete");
            // Preserve legacy cursor advancement even if the callback throws.
            ordered.get(next++).execute(frame);
        }

        @Override
        public boolean complete() {
            return next == ordered.size();
        }
    }
}
