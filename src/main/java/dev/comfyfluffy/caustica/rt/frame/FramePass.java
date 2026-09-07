package dev.comfyfluffy.caustica.rt.frame;

/** A single ordered operation in the linear frame pipeline. */
@FunctionalInterface
public interface FramePass {
    void execute(FrameContext frame);
}
