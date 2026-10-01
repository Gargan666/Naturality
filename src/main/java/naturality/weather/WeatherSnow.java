package naturality.weather;

/** Rain-channel thresholds for snowfall accumulation. */
public final class WeatherSnow {
    private WeatherSnow() {}
    public static int accumulationLimit(net.minecraft.server.level.ServerLevel level, int vanillaLimit) {
        var weather = WeatherSystem.state(level);
        if (!naturality.config.NaturalityServerConfig.get().weatherSnowAccumulation || weather == null || vanillaLimit <= 0) return vanillaLimit;
        return Math.min(7, layerLimit(weather.rain()));
    }
    public static int layerLimit(float rain) {
        if (rain >= 80) return Integer.MAX_VALUE;
        if (rain >= 60) return 4;
        if (rain >= 50) return 2;
        return 1;
    }
}
