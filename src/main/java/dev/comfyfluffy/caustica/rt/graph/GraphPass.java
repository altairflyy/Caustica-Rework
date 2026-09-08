package dev.comfyfluffy.caustica.rt.graph;

/** A named pass in the non-executing shadow graph. */
public record GraphPass(String name) {
    public GraphPass {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("graph pass name must not be blank");
        }
    }
}
