package naturality.weather;

/** Dimension weather changes gravity strength; Distortion is an individual effect. */
public record EndWeatherState(float gravity, int starfall, float rise) {
    public static final EndWeatherState CLEAR = new EndWeatherState(0, 0, 0);
    public float strength() { return 1 - .9F * Math.clamp(gravity / 100, 0, 1); }
    public float verticalGravity() { return strength(); }
}
