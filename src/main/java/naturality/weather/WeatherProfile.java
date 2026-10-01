package naturality.weather;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Per-world dimension pool/ranges and independently selectable manual channels. */
public final class WeatherProfile {
    public static final Codec<WeatherProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.optionalFieldOf("enabled", false).forGetter(p -> p.enabled),
        Codec.BOOL.optionalFieldOf("overrideRain", false).forGetter(p -> p.overrideRain),
        Codec.BOOL.optionalFieldOf("overrideWind", false).forGetter(p -> p.overrideWind),
        Codec.BOOL.optionalFieldOf("overrideTemperature", false).forGetter(p -> p.overrideTemperature),
        Codec.BOOL.optionalFieldOf("overrideDirection", false).forGetter(p -> p.overrideDirection),
        Codec.INT.optionalFieldOf("rain", 25).forGetter(p -> p.rain),
        Codec.INT.optionalFieldOf("wind", 0).forGetter(p -> p.wind),
        Codec.INT.optionalFieldOf("temperature", 50).forGetter(p -> p.temperature),
        Codec.INT.optionalFieldOf("direction", 0).forGetter(p -> p.direction),
        Codec.INT.optionalFieldOf("minWind", 0).forGetter(p -> p.minWind),
        Codec.INT.optionalFieldOf("maxWind", 100).forGetter(p -> p.maxWind),
        Codec.INT.optionalFieldOf("minTemperature", 15).forGetter(p -> p.minTemperature),
        Codec.INT.optionalFieldOf("maxTemperature", 75).forGetter(p -> p.maxTemperature)
    ).apply(instance, WeatherProfile::new));

    public volatile boolean enabled;
    public volatile boolean overrideRain, overrideWind, overrideTemperature, overrideDirection;
    public volatile int rain = 25, wind = 0, temperature = 50, direction = 0;
    public volatile int minWind = 0, maxWind = 100, minTemperature = 15, maxTemperature = 75;
    public WeatherProfile() { this(false); }
    public WeatherProfile(boolean enabled) { this.enabled = enabled; }
    private WeatherProfile(boolean enabled, boolean overrideRain, boolean overrideWind,
            boolean overrideTemperature, boolean overrideDirection, int rain, int wind,
            int temperature, int direction, int minWind, int maxWind,
            int minTemperature, int maxTemperature) {
        this.enabled = enabled;
        this.overrideRain = overrideRain;
        this.overrideWind = overrideWind;
        this.overrideTemperature = overrideTemperature;
        this.overrideDirection = overrideDirection;
        this.rain = rain;
        this.wind = wind;
        this.temperature = temperature;
        this.direction = direction;
        this.minWind = minWind;
        this.maxWind = maxWind;
        this.minTemperature = minTemperature;
        this.maxTemperature = maxTemperature;
        validate();
    }
    public void validate() {
        rain = Math.clamp(rain, 0, 100); wind = Math.clamp(wind, 0, 100); temperature = Math.clamp(temperature, 0, 100);
        direction = Math.clamp(direction, 0, 360);
        minWind = Math.clamp(minWind, 0, 100); maxWind = Math.clamp(maxWind, minWind, 100);
        minTemperature = Math.clamp(minTemperature, 0, 100); maxTemperature = Math.clamp(maxTemperature, minTemperature, 100);
    }
}
