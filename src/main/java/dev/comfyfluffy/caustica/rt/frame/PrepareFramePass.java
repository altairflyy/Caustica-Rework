package dev.comfyfluffy.caustica.rt.frame;

import java.util.Objects;
import java.util.function.Consumer;

/** State-only preparation performed immediately before frame recording. */
public final class PrepareFramePass implements FramePass {
    private final Consumer<FrameContext> delegate;

    public PrepareFramePass(Consumer<FrameContext> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void execute(FrameContext frame) {
        delegate.accept(Objects.requireNonNull(frame, "frame"));
    }
}
