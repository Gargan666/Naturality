package naturality.client.camera;

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
        float target = grounded ? 0 : (float)(Math.clamp(verticalSpeed / .42, -1, 1) * 2.5 * multiplier);
        pitch += (target - pitch) * .28F;
        if (target == 0 && Math.abs(pitch) < .001F) pitch = 0;
    }

    public float sample(float partialTick) {
        return previous + Math.clamp(partialTick, 0, 1) * (pitch - previous);
    }
    public void reset() { previous = pitch = 0; fallingTicks = 0; }
}
