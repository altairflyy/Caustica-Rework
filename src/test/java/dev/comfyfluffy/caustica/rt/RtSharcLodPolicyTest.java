package dev.comfyfluffy.caustica.rt;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class RtSharcLodPolicyTest {
    @Test
    void farGeometryUsesASeparateTwoBlockCacheDomain() throws IOException {
        String common = Files.readString(Path.of("shaders/world/world_common.slang"));
        String closestHit = Files.readString(Path.of("shaders/world/world.rchit.slang"));
        String sharc = Files.readString(Path.of("shaders/world/sharc.slang"));
        String raygen = Files.readString(Path.of("shaders/world/world.rgen.slang"));

        assertTrue(common.contains("PAYLOAD_FAR_GEOMETRY = 2048u"));
        assertTrue(closestHit.contains("payload.flags |= PAYLOAD_FAR_GEOMETRY;"));
        assertTrue(sharc.contains("return farGeometry ? 2.0 : push.sharcParams.x;"));
        assertTrue(sharc.contains("uint domainBits = farGeometry ? 0xA511E9B3u : 0u;"));
        assertTrue(sharc.contains("entry.normal.w - (farGeometry ? 1.0 : 0.0)"));
        assertTrue(raygen.contains("bool sharcFarSurface = payloadFarGeometry();"));
        assertTrue(raygen.contains("sharcWithinDistance(worldPush, hitPos)"));
    }
}
