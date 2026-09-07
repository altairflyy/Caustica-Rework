package dev.comfyfluffy.caustica.rt;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins migration-sensitive legacy behaviour before ownership starts moving out of RtComposite and
 * the DH/Voxy compatibility layer. These are characterization tests: they describe the reference,
 * not a preferred future design.
 */
final class RtRewriteCharacterizationTest {
    private static final Path REPO_ROOT = repoRoot();
    private static final Path COMPOSITE =
            REPO_ROOT.resolve("src/main/java/dev/comfyfluffy/caustica/rt/RtComposite.java");
    private static final Path RESTIR_HISTORY = REPO_ROOT.resolve(
            "src/main/java/dev/comfyfluffy/caustica/rt/lighting/RestirHistory.java");
    private static final Path LOD_TERRAIN = REPO_ROOT.resolve(
            "src/main/java/dev/comfyfluffy/caustica/rt/terrain/RtDistantHorizonsTerrain.java");
    private static final Path LOD_SELECTOR = REPO_ROOT.resolve(
            "src/main/java/dev/comfyfluffy/caustica/rt/lod/LodProviderSelector.java");
    private static final Path NRD = REPO_ROOT.resolve(
            "src/main/java/dev/comfyfluffy/caustica/rt/pipeline/RtNrdDenoiser.java");

    @Test
    void restirCurrentAndPreviousUseOppositePingPongHalves() throws IOException {
        String composite = Files.readString(COMPOSITE);
        String history = Files.readString(RESTIR_HISTORY);

        assertTrue(history.contains("reservoirs[writeIndex ^ 1].deviceAddress"),
                "the previous reservoir must be the opposite ping-pong half");
        assertTrue(history.contains("reservoirs[writeIndex].deviceAddress"),
                "the current reservoir must be the write-index half");
        assertTrue(composite.contains(
                        "restirPreviousAddress(), restirCurrentAddress()"),
                "the shader push must preserve previous/current argument order");
        assertTrue(history.contains("if (enabled) writeIndex ^= 1;"),
                "the ReSTIR ping-pong index must advance only by toggling halves");
    }

    @Test
    void legacyTemporalResetTriggersRemainCharacterized() throws IOException {
        String source = Files.readString(COMPOSITE);

        assertTrue(source.contains("svgfResources.resetHistory();"),
                "fresh/recreated SVGF resources must invalidate SVGF history");
        assertTrue(source.contains("mvHasPrev = false; // recreated images -> first MV frame is zero"),
                "resource recreation must invalidate motion-vector history");
        assertTrue(source.contains("RtNrdDenoiser.INSTANCE.resetHistory();"),
                "resolution-dependent NRD history must be reset on recreation");
        assertTrue(source.contains("broadcastTemporalReset(RtFsrUpscaler.INSTANCE::requestReset)"),
                "FSR must reset through the coordinator on the legacy camera-discontinuity path");
        assertTrue(source.contains("broadcastTemporalReset(RtXessUpscaler.INSTANCE::requestReset)"),
                "XeSS must reset through the coordinator on the legacy camera-discontinuity path");
        assertTrue(source.contains("private final TemporalState temporalState = new TemporalState();"),
                "the runtime must have one TemporalState coordinator");
        assertTrue(source.contains("temporalState.snapshot(frameContext);"),
                "the coordinator must receive the runtime FrameContext");
        assertTrue(source.contains("temporalState.broadcast(request -> legacyDelivery.run())"),
                "legacy reset delivery must pass through TemporalState.broadcast");
    }

    @Test
    void lodSourceVersionReuseAvoidsRebuildingUnchangedMeshes() throws IOException {
        String source = Files.readString(LOD_TERRAIN);

        assertMatches(source,
                "oldSource\\s*!=\\s*null\\s*&&\\s*oldSource\\.version\\s*==\\s*mesh\\.version\\(\\)"
                        + ".*?PlannedBatch\\.reuse\\(entry\\)",
                "an unchanged source key/version must reuse its already-built batches");
    }

    @Test
    void incompleteLodReplacementStartsFromThePublishedProxy() throws IOException {
        String source = Files.readString(LOD_TERRAIN);

        assertTrue(source.contains(
                        "new BuildSession(taskEpoch, revision, new ArrayDeque<>(plan), materials, current)"),
                "a replacement build must start from the currently published proxy");
        assertMatches(source,
                "if\\s*\\(base\\s*!=\\s*null\\)\\s*\\{.*?"
                        + "for\\s*\\(GeomEntry entry : base\\.entries\\)"
                        + "\\s*workingEntries\\.put\\(entry\\.batchKey, entry\\)",
                "the replacement working set must retain old proxy entries while new batches are incomplete");
        assertTrue(source.contains(
                        "Only the final checkpoint drops batches that DH no longer reports"),
                "stale batches must survive intermediate progressive checkpoints");
    }

    @Test
    void voxyOwnsTheHorizonBeforeDistantHorizonsFallback() throws IOException {
        String source = Files.readString(LOD_SELECTOR);

        assertMatches(source,
                "LodMeshSnapshot voxy\\s*=\\s*voxySource\\.snapshot\\(\\);"
                        + ".*?if\\s*\\(!voxy\\.meshes\\(\\)\\.isEmpty\\(\\)\\)\\s*\\{"
                        + ".*?Provider\\.VOXY"
                        + ".*?LodMeshSnapshot dh\\s*=\\s*dhSource\\.snapshot\\(\\);"
                        + ".*?Provider\\.DH",
                "a valid Voxy source snapshot must win before the DH source is queried");
    }

    @Test
    void temporalUpscalersAreMutuallyExclusiveWithReferencePriority() throws IOException {
        String source = Files.readString(COMPOSITE);

        assertTrue(source.contains(
                        "boolean rrPath = RtDlssRr.enabled() && debugView == 0;"),
                "DLSS-RR remains first in the temporal upscale slot");
        assertTrue(source.contains(
                        "boolean fsrPath = !rrPath && RtFsrUpscaler.enabled() && debugView == 0;"),
                "FSR may run only when DLSS-RR is not selected");
        assertTrue(source.contains(
                        "boolean xessPath = !rrPath && !fsrPath && RtXessUpscaler.enabled() && debugView == 0;"),
                "XeSS may run only when neither DLSS-RR nor FSR is selected");
    }

    @Test
    void nrdRemainsRetiredInTheReferenceBaseline() throws IOException {
        String source = Files.readString(NRD);

        assertMatches(source,
                "public\\s+static\\s+boolean\\s+enabled\\(\\)\\s*\\{\\s*return\\s+false\\s*;\\s*\\}",
                "NRD/REBLUR must remain disabled during the rewrite baseline");
    }

    private static void assertMatches(String source, String regex, String message) {
        assertTrue(Pattern.compile(regex, Pattern.DOTALL).matcher(source).find(), message);
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("shaders/world"))
                    && Files.isDirectory(candidate.resolve("src/main/java"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("could not locate the repository root from " + dir);
    }
}
