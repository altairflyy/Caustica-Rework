package dev.comfyfluffy.caustica.rt.graph;

/** Logical hand-off resource used only to describe pass ordering in shadow mode. */
public record GraphResource(String name) {
    public GraphResource {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("graph resource name must not be blank");
        }
    }
}
