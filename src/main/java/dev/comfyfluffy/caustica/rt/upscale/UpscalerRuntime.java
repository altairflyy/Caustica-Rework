package dev.comfyfluffy.caustica.rt.upscale;

/**
 * Runtime owner for the mutually exclusive temporal/native upscaler backends.
 *
 * <p>Selection and dispatch remain frame-orchestration decisions. This object
 * owns backend state and releases it before the shared vendor runtimes shut
 * down.</p>
 */
public final class UpscalerRuntime {
    public static final UpscalerRuntime INSTANCE = new UpscalerRuntime();

    private final FsrUpscalerBackend fsr = new FsrUpscalerBackend();
    private final XessUpscalerBackend xess = new XessUpscalerBackend();
    private final NativeUpscalerBackend nativeBackend = new NativeUpscalerBackend();

    private UpscalerRuntime() {
    }

    public FsrUpscalerBackend fsr() {
        return fsr;
    }

    public XessUpscalerBackend xess() {
        return xess;
    }

    public NativeUpscalerBackend nativeBackend() {
        return nativeBackend;
    }

    /** Preserve the legacy switch-away release point without exposing lifetime calls to the orchestrator. */
    public void releaseInactiveBackends() {
        fsr.releaseIfDisabled();
        xess.releaseIfDisabled();
    }

    public void destroy() {
        fsr.destroy();
        xess.destroy();
        nativeBackend.destroy();
    }
}
