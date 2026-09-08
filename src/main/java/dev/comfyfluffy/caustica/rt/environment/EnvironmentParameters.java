package dev.comfyfluffy.caustica.rt.environment;

import dev.comfyfluffy.caustica.rt.gen.WorldPushData.Float4;

import java.util.Objects;

/** Immutable snapshot of the environment values already materialized for one frame. */
public record EnvironmentParameters(
        int dimension,
        Time time,
        Weather weather,
        Sky sky,
        Fog fog,
        Clouds clouds
) {
    public EnvironmentParameters {
        time = Objects.requireNonNull(time, "time");
        weather = Objects.requireNonNull(weather, "weather");
        sky = Objects.requireNonNull(sky, "sky");
        fog = Objects.requireNonNull(fog, "fog");
        clouds = Objects.requireNonNull(clouds, "clouds");
    }

    public record Time(float partialTick, double gameTimeTicks) {}

    public record Weather(float rain, float thunder, float skyDarken, float lightAttenuation) {
        public static final Weather CLEAR = new Weather(0f, 0f, 1f, 1f);
    }

    public record Sky(Float4 sunDir, Float4 lightDir, Float4 lightRadiance, Float4 moonDir,
                      Float4 celestial, Float4 sunUv, Float4 moonUv) {}

    public record Fog(Float4 params, Float4 tint) {}

    public record Clouds(Float4 params, Float4 anchor, Float4 color) {
        public static final Clouds NONE = new Clouds(
                new Float4(0f, 0f, 0f, 0f), new Float4(0f, 0f, 0f, 0f),
                new Float4(1f, 1f, 1f, 0f));
    }
}
