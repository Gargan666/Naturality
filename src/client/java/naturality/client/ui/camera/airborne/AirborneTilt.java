package naturality.client.ui.camera.airborne;



/** Tick easing with render interpolation; positive pitch looks downward. */
public final class AirborneTilt {
    private float previous;
    private float pitch;
    private int fallingTicks;

    public void tick(boolean grounded, double verticalSpeed) {
        tick(grounded, verticalSpeed, false);
    }

    public void tick(boolean grounded, double verticalSpeed, boolean controlledFlight) {
        previous = pitch;
        fallingTicks = !grounded && !controlledFlight && verticalSpeed < -.03 ? Math.min(200, fallingTicks + 1) : 0;
        float multiplier = verticalSpeed < 0
            ? (float)Math.min(6, 1 + Math.max(0, -verticalSpeed / .42 - 1) * .5 + fallingTicks / 40.0) : 1;
        // Reach full rising tilt quickly, then release it before the jump apex.
        double velocityTilt = verticalSpeed > 0
            ? Math.clamp((verticalSpeed - .08) / .20, 0, 1)
            : Math.clamp(verticalSpeed / .42, -1, 0);
        float strength = verticalSpeed > 0 ? 1.0F : 2.5F;
        float target = grounded ? 0 : (float)(velocityTilt * strength * multiplier);
        float response = target > 0 && target > pitch ? .65F : .28F;
        pitch += (target - pitch) * response;
        if (target == 0 && Math.abs(pitch) < .001F) pitch = 0;
    }

    public float sample(float partialTick) {
        return previous + Math.clamp(partialTick, 0, 1) * (pitch - previous);
    }
    public void reset() { previous = pitch = 0; fallingTicks = 0; }

    public interface AirborneCameraState {
        float naturality$fallMultiplier(float partialTick);
    }
}
