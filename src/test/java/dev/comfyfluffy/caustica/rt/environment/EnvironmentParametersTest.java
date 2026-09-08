package dev.comfyfluffy.caustica.rt.environment;

import dev.comfyfluffy.caustica.rt.gen.WorldPushData.Float4;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class EnvironmentParametersTest {
    @Test
    void keepsEnvironmentDomainsDistinctInOneFrameSnapshot() {
        EnvironmentParameters.Time time = new EnvironmentParameters.Time(0.25f, 1200.25);
        EnvironmentParameters.Weather weather = new EnvironmentParameters.Weather(0.4f, 0.2f, 0.7f, 0.6f);
        EnvironmentParameters.Sky sky = new EnvironmentParameters.Sky(
                lane(1f), lane(2f), lane(3f), lane(4f), lane(5f), lane(6f), lane(7f));
        EnvironmentParameters.Fog fog = new EnvironmentParameters.Fog(lane(8f), lane(9f));
        EnvironmentParameters.Clouds clouds = new EnvironmentParameters.Clouds(lane(10f), lane(11f), lane(12f));

        EnvironmentParameters parameters = new EnvironmentParameters(2, time, weather, sky, fog, clouds);

        assertEquals(2, parameters.dimension());
        assertEquals(1200.25, parameters.time().gameTimeTicks());
        assertEquals(0.4f, parameters.weather().rain());
        assertEquals(lane(3f), parameters.sky().lightRadiance());
        assertEquals(lane(8f), parameters.fog().params());
        assertEquals(lane(11f), parameters.clouds().anchor());
        assertNotSame(EnvironmentParameters.Weather.CLEAR, parameters.weather());
    }

    @Test
    void clearAndNoCloudSentinelsPreserveLegacyNeutralValues() {
        assertEquals(1f, EnvironmentParameters.Weather.CLEAR.skyDarken());
        assertEquals(1f, EnvironmentParameters.Weather.CLEAR.lightAttenuation());
        assertEquals(lane(0f), EnvironmentParameters.Clouds.NONE.params());
        assertEquals(new Float4(1f, 1f, 1f, 0f), EnvironmentParameters.Clouds.NONE.color());
    }

    private static Float4 lane(float value) {
        return new Float4(value, value, value, value);
    }
}
