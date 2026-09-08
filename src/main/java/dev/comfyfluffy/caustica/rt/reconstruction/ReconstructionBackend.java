package dev.comfyfluffy.caustica.rt.reconstruction;

/**
 * Lifecycle contract for one temporal reconstruction implementation.
 *
 * <p>The request type is deliberately backend-specific. SVGF and DLSS-RR do
 * not consume the same guides or control data, so implementations must not be
 * forced through a synthetic union request containing unused inputs.</p>
 *
 * @param <I> immutable request type owned by the implementation
 */
public interface ReconstructionBackend<I> {
    /** Whether this backend can be selected with its current configuration. */
    boolean available();

    /** Request that temporal history be discarded before the next execution. */
    void requestReset();

    /** Execute or record this backend for one frame. */
    ReconstructionResult execute(I input);

    /** Release backend-owned resources. Implementations must be idempotent. */
    void destroy();
}
