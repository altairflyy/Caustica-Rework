package dev.comfyfluffy.caustica.rt.graph;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PostProcessingOwnershipTest {
    private static final Path ROOT = Path.of("src/main/java/dev/comfyfluffy/caustica/rt");

    @Test void productionUsesOnePostOwnerAndPreservesSplitLifecycle() throws Exception {
        String source = Files.readString(ROOT.resolve("RtComposite.java"));
        String traceFrames = Files.readString(ROOT.resolve("trace/TraceFrameResources.java"));
        assertTrue(source.contains("private final PostProcessing postProcessing = new PostProcessing()"));
        assertTrue(source.contains("private final TraceFrameResources traceFrameResources = new TraceFrameResources("));
        for (String field : new String[]{"private RtDisplayPipeline displayPipeline",
                "private RtImage displayImage", "private RtImage hdrDisplayImage", "new RtExposure()"}) {
            assertFalse(source.contains(field), field);
        }
        ordered(source.substring(source.indexOf("private void ensureOutput(")),
                "ctx.waitIdle()", "postProcessing.releaseImagesForResize()",
                "traceFrameResources.releasePrimaryAfterIdle()", "restirSystem.destroy()",
                "traceFrameResources.releaseGuidesAfterIdle()", "svgfBackend.releaseResources()",
                "traceFrameResources.releaseReconstructionOutputsAfterIdle()",
                "traceFrameResources.createPrimary(ctx, configuration, optimal)", "syncRestirResources(ctx)",
                "postProcessing.createImages(ctx, width, height)", "traceFrameResources.createGuides(ctx)",
                "svgfBackend.ensureResources(ctx", "traceFrameResources.createReconstructionOutputs(ctx)",
                "nrdBackend.bindCombine(ctx", "postProcessing.ensureExposure(ctx)",
                "broadcastTemporalReset", "worldTraceResources.bindFrameViews(traceFrameViews())",
                "postProcessing.bind(views.rrOutput())");
        ordered(source.substring(source.indexOf("public void destroy()")),
                "postProcessing.destroyImages()", "RtWorldOverlay.INSTANCE.destroy()",
                "traceFrameResources.release()", "restirSystem.destroy()",
                "postProcessing.destroyPipelineAndExposure()");
        for (String field : new String[]{"private RtImage output", "private RtBuffer continuationQueue",
                "private RtImage gNormal", "private RtImage nrdDiffOut", "private RtImage rrOutput"}) {
            assertFalse(source.contains(field), field);
            assertTrue(traceFrames.contains(field), field);
        }
        assertFalse(source.contains("output.destroy()"));
        assertFalse(source.contains("destroyGuideImages()"));
        assertTrue(source.contains("postProcessing.record(pipelineContext, pipelineCommand, pipelineStack, frameViews().rrOutput()"));
        assertTrue(source.contains("() -> hdrWrittenThisFrame = postHdr"));
    }

    @Test void postOwnerPreservesExposureDispatchBarrierAndCompletionOrder() throws Exception {
        String owner = Files.readString(ROOT.resolve("post/PostProcessing.java"));
        ordered(owner.substring(owner.indexOf("public void record(")),
                "exposure.record(ctx, cmd, stack, rrOutput, postHdr)", "PostBarrierPlan.DISPLAY",
                "displayPipeline.dispatch(cmd, displayW, displayH, postHdr",
                "displayWritten.run()", "PostBarrierPlan.COPY", "VK10.vkCmdCopyImage",
                "PostBarrierPlan.EXPORT");
        ordered(owner.substring(owner.indexOf("public void createImages(")),
                "VK10.VK_FORMAT_R8G8B8A8_UNORM", "VK10.VK_FORMAT_R16G16B16A16_SFLOAT");
        ordered(owner.substring(owner.indexOf("public void destroyPipelineAndExposure()")),
                "exposure.destroy()", "displayPipeline.destroy()");
        assertTrue(owner.contains("displayImage.view, rrOutput.view, exposure.image().view, hdrDisplayImage.view"));
        assertFalse(owner.contains("waitIdle("));
        assertFalse(owner.contains("VulkanCommandEncoder.memoryBarrier("));
        assertTrue(owner.contains("copyRegion(stack, displayW, displayH)"));
        assertTrue(owner.contains("region.get(0).extent().set(width, height, 1)"));
    }

    private static void ordered(String source, String... tokens) {
        int position = -1;
        for (String token : tokens) {
            int found = source.indexOf(token, position + 1);
            assertTrue(found > position, "Missing/out-of-order: " + token);
            position = found;
        }
    }
}
