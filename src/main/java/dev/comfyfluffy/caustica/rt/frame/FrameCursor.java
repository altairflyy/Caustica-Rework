package dev.comfyfluffy.caustica.rt.frame;

/** Incremental execution preserves the recording work between macro-pass callbacks. */
public interface FrameCursor {
    void executeNext();
    boolean complete();
}
