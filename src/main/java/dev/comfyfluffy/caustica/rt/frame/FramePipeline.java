package dev.comfyfluffy.caustica.rt.frame;

import java.util.List;
import java.util.Objects;

/**
 * Small ordered frame-pass runner. This is intentionally a linear seam, not a
 * render graph: pass ownership and GPU resources remain with the legacy path.
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

    public void execute(FrameContext frame) {
        Cursor cursor = begin(frame);
        while (!cursor.complete()) {
            cursor.executeNext();
        }
    }

    public Cursor begin(FrameContext frame) {
        return new Cursor(Objects.requireNonNull(frame, "frame"));
    }

    public int passCount() {
        return passes.size();
    }

    /** Immutable callback bindings shared with the graph's compiled execution order. */
    public List<FramePass> passes() {
        return passes;
    }

    /** Stable debug description used by the shadow render graph. */
    public List<String> declaredPassNames() {
        return passes.stream().map(pass -> pass.getClass().getSimpleName()).toList();
    }

    /** Executes this pipeline incrementally while preserving its declared order. */
    public final class Cursor implements FrameCursor {
        private final FrameContext frame;
        private int nextPass;

        private Cursor(FrameContext frame) {
            this.frame = frame;
        }

        public void executeNext() {
            if (complete()) {
                throw new IllegalStateException("frame pipeline is already complete");
            }
            passes.get(nextPass++).execute(frame);
        }

        public boolean complete() {
            return nextPass == passes.size();
        }
    }
}
