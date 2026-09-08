package dev.comfyfluffy.caustica.rt.scene;

/**
 * Immutable description of weather geometry appended to the shared particle capture.
 *
 * <p>Rain and snow remain part of the particle BLAS and its single dynamic TLAS instance. This
 * contribution identifies their exact vertex segment so scene/AS preparation can attach zero motion
 * and accounting without owning reconstruction details.</p>
 */
public record WeatherSceneContribution(int columnCount, int firstVertex, int vertexCount) {
    public WeatherSceneContribution {
        if (columnCount < 0 || firstVertex < 0 || vertexCount < 0) {
            throw new IllegalArgumentException("weather contribution values must be non-negative");
        }
    }

    public static WeatherSceneContribution empty(int firstVertex) {
        return new WeatherSceneContribution(0, firstVertex, 0);
    }

    public int endVertex() {
        return firstVertex + vertexCount;
    }
}
