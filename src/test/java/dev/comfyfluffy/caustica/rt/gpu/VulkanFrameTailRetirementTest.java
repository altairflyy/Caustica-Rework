package dev.comfyfluffy.caustica.rt.gpu;

import com.mojang.blaze3d.vulkan.Destroyable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VulkanFrameTailRetirementTest {
    @Test
    void registersExactlyOnceWithoutDestroyingEagerly() {
        List<Destroyable> registered = new ArrayList<>();
        AtomicInteger destroys = new AtomicInteger();
        FrameTailRetirement retirement = new VulkanFrameTailRetirement(registered::add);

        retirement.retire(destroys::incrementAndGet);

        assertEquals(1, registered.size());
        assertEquals(0, destroys.get());
        registered.getFirst().destroy();
        assertEquals(1, destroys.get());
    }
}
