package dev.comfyfluffy.caustica.rt.trace;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class IndirectShaderVariantTest {
    @Test
    void dlssRayReconstructionWithoutNrdUsesLeanVariant() {
        assertEquals(IndirectShaderVariant.DLSS_RR_NO_NRD,
                IndirectShaderVariant.select(true, false));
        assertEquals(IndirectShaderVariant.DLSS_RR_NO_NRD,
                IndirectShaderVariant.select(true, false, true));
    }

    @Test
    void nrdAlwaysKeepsGenericVariant() {
        assertEquals(IndirectShaderVariant.GENERIC,
                IndirectShaderVariant.select(true, true));
        assertEquals(IndirectShaderVariant.GENERIC,
                IndirectShaderVariant.select(false, true));
    }

    @Test
    void nonDlssPathKeepsGenericVariant() {
        assertEquals(IndirectShaderVariant.GENERIC,
                IndirectShaderVariant.select(false, false));
    }

    @Test
    void abOverrideCanForceGenericWithoutChangingRenderFeatures() {
        assertEquals(IndirectShaderVariant.GENERIC,
                IndirectShaderVariant.select(true, false, false));
    }

    @Test
    void sbtIndicesRemainPrimaryGenericLean() {
        assertEquals(1, IndirectShaderVariant.GENERIC.raygenIndex());
        assertEquals(2, IndirectShaderVariant.DLSS_RR_NO_NRD.raygenIndex());
    }

    @Test
    void buildPublishesGenericAndLeanVariantsForBothSerModes() throws Exception {
        String build = Files.readString(Path.of("build.gradle"));
        assertEquals(true, build.contains("world_lean.rgen.spv"));
        assertEquals(true, build.contains("world_ser_lean.rgen.spv"));
        assertEquals(true, build.contains("-DCAUSTICA_INDIRECT_NO_NRD"));

        String resources = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/rt/trace/WorldTraceResources.java"));
        assertEquals(true, resources.contains("RtDeviceBringup.worldRaygenShader()"));
        assertEquals(true, resources.contains("RtDeviceBringup.worldLeanRaygenShader()"));
    }

    @Test
    void leanCompileGuardCoversNrdStateWhileSharcRemainsPresent() throws Exception {
        String raygen = Files.readString(Path.of("shaders/world/world.rgen.slang"));
        assertEquals(true, raygen.contains("#ifndef CAUSTICA_INDIRECT_NO_NRD"));
        assertEquals(true, raygen.contains("float3 Ldiff"));
        assertEquals(true, raygen.contains("int pendingHitChannel"));
        assertEquals(true, raygen.contains("SharcPathState sharcState = sharcInitPath()"));
        assertEquals(true, raygen.contains("restirSpatiotemporal"));
    }
}
