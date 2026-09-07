package dev.comfyfluffy.caustica.rt.frame;

import java.util.Objects;
import java.util.function.Consumer;

/** Initial pipeline pass that delegates to the existing composite recording path. */
public final class LegacyCompositePass implements FramePass {
    private final Consumer<FrameContext> delegate;

    public LegacyCompositePass(Consumer<FrameContext> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void execute(FrameContext frame) {
        delegate.accept(Objects.requireNonNull(frame, "frame"));
    }
}
