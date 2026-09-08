package dev.comfyfluffy.caustica.rt.graph;

import java.util.List;
import java.util.Set;

import static dev.comfyfluffy.caustica.rt.graph.BarrierSynthesis.*;

/**
 * Caustica-owned upscale seams only. SDK histories and SDK layout transitions
 * remain opaque, cross-frame dependencies owned by the corresponding SDK.
 * Inputs are current-frame images, output is the distinct display-size rrOutput.
 */
public final class UpscalerBarrierPlan {
    public enum Backend { NATIVE, DLSS_RR, FSR, XESS }
    private UpscalerBarrierPlan() {}

    private static Use use(String name, Mode mode, Stage stage) {
        return new Use(name, mode, stage, Layout.GENERAL);
    }

    public static List<Operation> operations(Backend backend) {
        Stage producer = backend == Backend.NATIVE ? Stage.TRANSFER : Stage.EXTERNAL;
        List<Use> inputs = switch (backend) {
            case NATIVE -> List.of(use("currentColor", Mode.READ, producer),
                    use("rrOutput", Mode.WRITE, producer));
            case FSR, XESS -> List.of(use("currentColor", Mode.READ, producer),
                    use("depth", Mode.READ, producer), use("motion", Mode.READ, producer),
                    use("rrOutput", Mode.WRITE, producer));
            case DLSS_RR -> List.of(use("currentColor", Mode.READ, producer),
                    use("depth", Mode.READ, producer), use("motion", Mode.READ, producer),
                    use("diffuseAlbedo", Mode.READ, producer), use("specularAlbedo", Mode.READ, producer),
                    use("normal", Mode.READ, producer), use("specularMotion", Mode.READ, producer),
                    use("rrOutput", Mode.WRITE, producer));
        };
        // Entry describes prior producers; their existing barriers remain authoritative.
        return List.of(new Operation("entry", List.of(use("currentColor", Mode.WRITE, Stage.EXTERNAL))),
                new Operation("produce", inputs),
                new Operation("export", List.of(use("rrOutput", Mode.READ, Stage.COMPUTE))));
    }

    public static Barrier before(Backend backend, String operation) {
        return compile(operations(backend), Set.of("depth", "motion", "diffuseAlbedo",
                "specularAlbedo", "normal", "specularMotion")).stream()
                .filter(b -> b.beforeOperation().equals(operation)).findFirst().orElseThrow();
    }

    private static List<Barrier> compile(List<Operation> operations, Set<String> imported) {
        return BarrierSynthesis.compile(operations, imported);
    }
}
