package naturality.client.sky;

import java.util.ArrayList;
import java.util.List;
import naturality.sky.SkyEventType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.EndFlashState;
import net.minecraft.client.resources.sounds.DirectionalSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/** One world-owned flash state shared by the sky, lightmap and sound scheduler. */
public final class EndFlashes {
    private static int drawnFlashes;
    private EndFlashes() {}
    public record Flash(float intensity, float xAngle, float yAngle, float size) {
        public float lightIntensity() { return intensity * size; }
    }
    /** Retains vanilla's seeded parameters and sine curve, compressing only the active pulse. */
    public static final class Pulse extends EndFlashState {
        private long seed = Long.MIN_VALUE;
        private int offset;
        private boolean captured;
        private float size = 1, peak = 1, speed = 1, gain;
        public void tick(long time, float strength) {
            tick(time, strength, 1);
        }
        public void tick(long time, float strength, float weight) {
            long nextSeed = time / 600;
            if (seed != nextSeed) {
                seed = nextSeed;
                var random = RandomSource.createThreadLocalInstance(seed);
                random.nextFloat();
                offset = random.nextIntBetweenInclusive(0, 200);
                captured = false;
            }
            long phase = time % 600;
            if (!captured && phase >= offset) {
                float amount = Math.clamp((strength - 10) / 10, 0, 1);
                var random = RandomSource.create(seed ^ 0x6A09E667F3BCC909L);
                random.nextFloat(); // Discard the correlated first sample of neighboring Java RNG seeds.
                float roll = random.nextFloat();
                size = 1 + amount * (.4F + 1.8F * roll * roll * roll - 1);
                peak = 1 + amount * (random.nextFloat() * .4F - .2F);
                speed = 1 + 3 * amount;
                gain = strength > .01F ? power(strength) * weight : 0;
                captured = true;
            }
            long mapped = captured ? Math.min(599, offset + (long)((phase - offset) * speed)) : phase;
            super.tick(seed * 600 + mapped);
        }
        public float size() { return size; }
        public float speed() { return speed; }
        public float eventIntensity(float partial) { return gain == 0 ? 0 : getIntensity(partial) * gain; }
        public boolean audibleStart() { return gain > .01F && flashStartedThisTick(); }
        @Override public float getIntensity(float partial) { return super.getIntensity(partial) * peak; }
    }
    private static State current() {
        var level = Minecraft.getInstance().level;
        return level != null && level.endFlashState() instanceof State state ? state : null;
    }
    public static float strength() {
        var level = Minecraft.getInstance().level;
        return level != null && level.dimension().equals(Level.END)
            ? SkyEventsClient.strength(level, SkyEventType.END_FLASHES) : 0;
    }
    public static float power(float strength) {
        return strength <= 10 ? Math.clamp(strength / 10, 0, 1)
            : 1 + .6F * Math.clamp((strength - 10) / 10, 0, 1);
    }
    public static float extraWeight(int index, float strength) {
        return Math.clamp((strength - 10 - 2 * index) / 2, 0, 1);
    }
    public static void beginFrame() { drawnFlashes = 0; }
    public static void flashDrawn() { drawnFlashes++; }
    public static int drawnFlashes() { return drawnFlashes; }
    public static List<Flash> visible(float partial, float darkness) {
        var state = current();
        return state == null ? List.of() : state.visible(partial, darkness);
    }
    /** Replaces ClientLevel's vanilla instance, so every consumer sees the event. */
    public static final class State extends EndFlashState {
        private final ClientLevel level;
        private final Pulse[] pulses = new Pulse[6];
        private final RandomSource soundRandom = RandomSource.create();
        private final List<PendingSound> sounds = new ArrayList<>();
        private int ticks, soundsScheduled;
        private record PendingSound(EventSound sound, int due) {}
        private final class EventSound extends DirectionalSoundInstance {
            private boolean cancelled;
            EventSound(EndFlashState pulse) {
                super(SoundEvents.WEATHER_END_FLASH, SoundSource.WEATHER, soundRandom,
                    Minecraft.getInstance().gameRenderer.mainCamera(), pulse.getXAngle(), pulse.getYAngle());
            }
            @Override public boolean canPlaySound() {
                return !cancelled && Minecraft.getInstance().level == level;
            }
        }
        public State(ClientLevel level) {
            this.level = level;
            java.util.Arrays.setAll(pulses, _ -> new Pulse());
        }
        private float strength() { return SkyEventsClient.strength(level, SkyEventType.END_FLASHES); }
        private float weight(int index) { return index == 0 ? 1 : extraWeight(index - 1, strength()); }
        @Override public void tick(long clockTime) {
            var client = Minecraft.getInstance();
            var manager = client.getSoundManager();
            ticks++;
            sounds.removeIf(sound -> ticks > sound.due() && !manager.isActive(sound.sound()));
            for (int i = 0; i < pulses.length; i++) {
                // Small clock offsets repeat the same seed/position across streams.
                // Stream zero uses vanilla's clock; extras use independent distant seeds.
                pulses[i].tick(clockTime + i * 104729L * 600L + i * 100L, strength(), weight(i));
                if (pulses[i].audibleStart()
                        && !(client.gui.screen() instanceof net.minecraft.client.gui.screens.WinScreen)) {
                    var sound = new EventSound(pulses[i]);
                    int delay = Math.max(1, Math.round(EndFlashState.SOUND_DELAY_IN_TICKS / pulses[i].speed()));
                    manager.playDelayed(sound, delay);
                    sounds.add(new PendingSound(sound, ticks + delay));
                    soundsScheduled++;
                }
            }
        }
        public void stopSounds() {
            for (var pending : sounds) {
                pending.sound().cancelled = true;
                Minecraft.getInstance().getSoundManager().stop(pending.sound());
            }
            sounds.clear();
        }
        // Suppress ClientLevel's second sound scheduler; each pulse is handled above.
        @Override public boolean flashStartedThisTick() { return false; }
        @Override public float getIntensity(float partial) {
            float result = 0;
            for (int i = 0; i < pulses.length; i++)
                result = Math.max(result, pulses[i].eventIntensity(partial) * pulses[i].size());
            return result;
        }
        @Override public float getXAngle() { return pulses[0].getXAngle(); }
        @Override public float getYAngle() { return pulses[0].getYAngle(); }
        public int soundsScheduled() { return soundsScheduled; }
        public List<Flash> visible(float partial, float darkness) {
            var flashes = new ArrayList<Flash>();
            for (int i = 0; i < pulses.length; i++) {
                float intensity = pulses[i].eventIntensity(partial) * darkness;
                if (intensity > 1.0E-5F) flashes.add(new Flash(intensity,
                    pulses[i].getXAngle(), pulses[i].getYAngle(), pulses[i].size()));
            }
            return flashes;
        }
    }
}
