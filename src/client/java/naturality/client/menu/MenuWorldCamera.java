package naturality.client.menu;

/** A menu orbit camera; all distances are relative to the viewport. */
public final class MenuWorldCamera {
    private float yaw = 35, pitch = 34;
    private double seconds;
    private long lastFrame;
    private boolean held;
    private double yawVelocity, pitchVelocity;
    private long lastDrag;

    public void frame(double speed) {
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : Math.clamp((now - lastFrame) / 1.0e9, 0, .1);
        lastFrame = now;
        advance(dt, speed);
    }

    public void advance(double dt, double speed) {
        seconds += dt;
        yaw = wrap(yaw + (float)(dt * speed * 3));
        if (!held) {
            double decay = Math.exp(-2.2 * dt), travel = (1 - decay) / 2.2;
            yaw = wrap(yaw + (float)(yawVelocity * travel));
            float wanted = pitch + (float)(pitchVelocity * travel);
            pitch = Math.clamp(wanted, -65, 85);
            yawVelocity *= decay;
            pitchVelocity = wanted == pitch ? pitchVelocity * decay : 0;
        }
    }
    public void hold() { held = true; lastDrag = 0; yawVelocity = pitchVelocity = 0; }
    public void release() {
        held = false;
        if (System.nanoTime() - lastDrag > 120_000_000) yawVelocity = pitchVelocity = 0;
    }
    public void suspend() { held = false; yawVelocity = pitchVelocity = 0; lastFrame = 0; }
    public void drag(double dx, double dy, int width, int height) {
        double horizontal = dx * 180 / Math.max(1, width), vertical = dy * 120 / Math.max(1, height);
        long now = System.nanoTime();
        double dt = lastDrag == 0 ? 1 / 60.0 : Math.clamp((now - lastDrag) / 1.0e9, 1 / 240.0, .1);
        double blend = lastDrag == 0 ? 1 : 1 - Math.exp(-25 * dt);
        yawVelocity += (Math.clamp(horizontal / dt, -360, 360) - yawVelocity) * blend;
        pitchVelocity += (Math.clamp(vertical / dt, -240, 240) - pitchVelocity) * blend;
        lastDrag = now;
        yaw = wrap(yaw + (float)horizontal);
        pitch = Math.clamp(pitch + (float)vertical, -65, 85);
    }
    private static float wrap(float angle) { return (angle % 360 + 360) % 360; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    /** Cloud blend at the level orbit: above/below starts immediately on either side.
     * The existing cap shader finishes its soft transition within three degrees. */
    public float cloudLayerPosition() {
        if (pitch == 0) return .5F;
        return pitch > 0 ? 1 + pitch / 12F : pitch / 12F;
    }
    public double seconds() { return seconds; }
    public boolean held() { return held; }
}
