package naturality.weather;

/** Independent channels. Temperature is a climate offset: 50 preserves biome temperature. */
public record WeatherState(float rain, float wind, float temperature, float direction) {
    public static final WeatherState CLEAR = new WeatherState(0, 0, 50, 0);
    public WeatherState {
        rain = clamp(rain); wind = clamp(wind); temperature = clamp(temperature);
        direction = Float.isFinite(direction) ? direction % 360 + (direction % 360 < 0 ? 360 : 0) : 0;
    }
    private static float clamp(float value) { return Float.isFinite(value) ? Math.clamp(value, 0, 100) : 50; }
    public float rainLevel() { return Math.min(1, rain / 25); }
    public float thunderLevel() { return Math.clamp((rain - 25) / 25, 0, 1); }
    public float precipitationDensity() {
        float heavy = Math.max(0, (rain - 50) / 50);
        return rain <= 50 ? rain / 25 : 2 + 6 * heavy * heavy * heavy;
    }
    /** Heavy rain fog fades in from slider 90 and reaches full strength at 100. */
    public float heavyRainFog() { return Math.clamp((rain - 90) / 10, 0, 1); }
    /** Keep indoor rain haze broad and faint; fully exposed rain ends at 15 blocks. */
    public float heavyRainFogEnd(float outdoorExposure) {
        float strength = heavyRainFog() * Math.clamp(outdoorExposure, 0, 1);
        return 100 - 85 * strength;
    }
    /** Heavy rain sprite likelihood grows with fog strength and distance into the fog. */
    public float heavyRainParticleChance(double distance) {
        return heavyRainFog() * Math.clamp((float)(distance / 15.0), 0, 1);
    }
    /** Probability of retaining a vanilla ground impact; does not alter rain sounds. */
    public float groundImpactChance() { float t = rain / 100; return t * t; }
    /** Wind motion reaches its former maximum at 75, then rises sharply to 3x at 100. */
    public float windEffect() {
        if (wind <= 75) return wind / 75;
        float severe = (wind - 75) / 25;
        return 1 + 2 * severe * severe;
    }
    public float windX() { return (float)Math.cos(Math.toRadians(direction)) * windEffect(); }
    public float windZ() { return (float)Math.sin(Math.toRadians(direction)) * windEffect(); }
    public float weakWindGain() { return wind / 100 * (1 - strongBlend()); }
    public float strongWindGain() { return wind / 100 * strongBlend(); }
    private float strongBlend() { float t = Math.clamp((wind - 30) / 50, 0, 1); return t * t * (3 - 2 * t); }
}
