package dev.comfyfluffy.caustica.rt.lighting;

import dev.comfyfluffy.caustica.CausticaConfig;
import dev.comfyfluffy.caustica.rt.RtContext;
import dev.comfyfluffy.caustica.rt.gen.WorldPushData.Int4;

/** Functional ReSTIR facade over the extracted two-buffer history state. */
public final class RestirSystem {
    private final RestirHistory history = new RestirHistory();

    public boolean featureEnabled() {
        return CausticaConfig.Rt.Lights.RESTIR_SAMPLING.value();
    }

    public void sync(RtContext context, int renderWidth, int renderHeight) {
        boolean desired = featureEnabled()
                && CausticaConfig.Rt.Lights.RIS_CANDIDATES.value() > 0
                && renderWidth > 0 && renderHeight > 0;
        history.ensure(context, renderWidth, renderHeight, desired);
    }

    /** Takes one host-side snapshot of every binding and live tuning lane used by the trace. */
    public Bindings bindings() {
        int mode = history.enabled() && featureEnabled() ? 1 : 0;
        Int4 tuning = new Int4(
                CausticaConfig.Rt.Lights.RESTIR_TEMPORAL_HISTORY.value(),
                CausticaConfig.Rt.Lights.RESTIR_SPATIAL_NEIGHBOURS.value(),
                CausticaConfig.Rt.Lights.RESTIR_MAX_AGE.value(), 0);
        return new Bindings(history.previousAddress(), history.currentAddress(), mode, tuning);
    }

    public void advance() {
        history.advance();
    }

    public void destroy() {
        history.destroy();
    }

    public record Bindings(long previousAddress, long currentAddress, int mode, Int4 tuning) {}
}
