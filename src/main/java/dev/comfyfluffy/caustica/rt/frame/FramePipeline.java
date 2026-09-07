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
        Objects.requireNonNull(frame, "frame");
        for (FramePass pass : passes) {
            pass.execute(frame);
        }
    }

    public int passCount() {
        return passes.size();
    }
}
