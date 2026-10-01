package naturality.sky;

public final class SkyEventSettings {
    public volatile boolean override = false;
    public volatile int strength = 10;
    public void validate() { strength = Math.clamp(strength, 0, 20); }
}
