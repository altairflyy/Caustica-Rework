package dev.comfyfluffy.caustica.rt.gpu;

/**
 * Retirement boundary for resources referenced by Minecraft's persistent frame-tail submission.
 * Implementations register destruction for completion; they must not execute it eagerly.
 */
@FunctionalInterface
public interface FrameTailRetirement {
    void retire(Runnable destruction);
}
