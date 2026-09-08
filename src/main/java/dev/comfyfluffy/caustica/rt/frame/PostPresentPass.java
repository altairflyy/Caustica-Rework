package dev.comfyfluffy.caustica.rt.frame;

import java.util.Objects;
import java.util.function.Consumer;

/** Records exposure, display mapping, and the final native-target copy. */
public final class PostPresentPass implements FramePass {
    private final Consumer<FrameContext> delegate;

    public PostPresentPass(Consumer<FrameContext> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void execute(FrameContext frame) {
        delegate.accept(Objects.requireNonNull(frame, "frame"));
    }
}
