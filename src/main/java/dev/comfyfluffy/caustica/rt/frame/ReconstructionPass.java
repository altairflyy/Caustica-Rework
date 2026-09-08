package dev.comfyfluffy.caustica.rt.frame;

import java.util.Objects;
import java.util.function.Consumer;

/** Selects and records the existing DLSS-RR or SVGF reconstruction path. */
public final class ReconstructionPass implements FramePass {
    private final Consumer<FrameContext> delegate;

    public ReconstructionPass(Consumer<FrameContext> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void execute(FrameContext frame) {
        delegate.accept(Objects.requireNonNull(frame, "frame"));
    }
}
