package dev.comfyfluffy.caustica.rt.frame;

import java.util.Objects;
import java.util.function.Consumer;

/** Records the existing FSR, XeSS, or native fallback upscale slot. */
public final class UpscalePass implements FramePass {
    private final Consumer<FrameContext> delegate;

    public UpscalePass(Consumer<FrameContext> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void execute(FrameContext frame) {
        delegate.accept(Objects.requireNonNull(frame, "frame"));
    }
}
