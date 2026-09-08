package dev.comfyfluffy.caustica.rt.lighting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RestirSystemTest {
    @Test
    void unallocatedSystemPublishesSafeBindings() {
        RestirSystem.Bindings bindings = new RestirSystem().bindings();

        assertEquals(0L, bindings.previousAddress());
        assertEquals(0L, bindings.currentAddress());
        assertEquals(0, bindings.mode());
    }
}
