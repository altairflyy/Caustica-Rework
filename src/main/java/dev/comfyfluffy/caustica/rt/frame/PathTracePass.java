package dev.comfyfluffy.caustica.rt.frame;

import java.util.Objects;
import java.util.function.Consumer;

/** Records the existing primary and indirect path-trace dispatches. */
public final class PathTracePass implements FramePass {
    private final Consumer<FrameContext> delegate;

    public PathTracePass(Consumer<FrameContext> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void execute(FrameContext frame) {
        delegate.accept(Objects.requireNonNull(frame, "frame"));
    }
}
