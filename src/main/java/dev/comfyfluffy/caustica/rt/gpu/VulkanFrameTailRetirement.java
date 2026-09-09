package dev.comfyfluffy.caustica.rt.gpu;

import com.mojang.blaze3d.vulkan.Destroyable;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;

import java.util.Objects;
import java.util.function.Consumer;

/** Uses Blaze3D's persistent-encoder destruction queue and its real submit timeline. */
public final class VulkanFrameTailRetirement implements FrameTailRetirement {
    private final Consumer<Destroyable> registration;

    public VulkanFrameTailRetirement(VulkanCommandEncoder encoder) {
        this(Objects.requireNonNull(encoder, "encoder")::queueForDestroy);
    }

    VulkanFrameTailRetirement(Consumer<Destroyable> registration) {
        this.registration = Objects.requireNonNull(registration, "registration");
    }

    @Override
    public void retire(Runnable destruction) {
        Objects.requireNonNull(destruction, "destruction");
        registration.accept(destruction::run);
    }
}
