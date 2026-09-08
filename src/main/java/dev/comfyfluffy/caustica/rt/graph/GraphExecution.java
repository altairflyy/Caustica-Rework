package dev.comfyfluffy.caustica.rt.graph;

import dev.comfyfluffy.caustica.rt.frame.FrameContext;
import dev.comfyfluffy.caustica.rt.frame.FrameCursor;
import dev.comfyfluffy.caustica.rt.frame.FramePass;
import dev.comfyfluffy.caustica.rt.frame.FramePipeline;
import dev.comfyfluffy.caustica.rewrite.RewriteGates;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/** Executes graph-selected callbacks; all barriers remain inside the legacy callbacks. */
public final class GraphExecution {
    private final FramePipeline legacy;
    private final List<FramePass> ordered;

    public GraphExecution(FrameGraph graph, FramePipeline legacy) {
        this.legacy = Objects.requireNonNull(legacy, "legacy");
        var bindings = new HashMap<String, FramePass>();
        for (FramePass pass : legacy.passes()) {
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

    /** Select once at the frame boundary; changing a property cannot switch a running cursor. */
    public FrameCursor begin(FrameContext frame) {
        Objects.requireNonNull(frame, "frame");
        return RewriteGates.renderGraphV2() ? new Cursor(frame) : legacy.begin(frame);
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
