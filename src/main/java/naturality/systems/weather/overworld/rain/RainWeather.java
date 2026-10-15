package naturality.systems.weather.overworld.rain;

import naturality.systems.weather.framework.WeatherSystem.ChannelDefinition;

/** Rain item registration; the framework owns clocks, persistence and interpolation. */
@org.jspecify.annotations.NullMarked
public final class RainWeather {
    public static final ChannelDefinition CYCLE = new ChannelDefinition(
        0x5241494EL, 1 - Math.pow(.6, 1.0 / 12000), 6000, 18000, 5000, _ -> 0, _ -> 100);

    private RainWeather() {}
}
