package naturality.client.weather;

import java.util.ArrayList;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import naturality.weather.WeatherShelter;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.util.RandomSource;

/** Each listener schedules thunder locally, including multiplayer visual strikes. */
public final class ThunderAudio {
    private record Pending(Vec3 position, SoundEvent sound, SoundSource source,
                           float volume, float pitch, long due) {}
    private static final ArrayList<Pending> pending = new ArrayList<>();
    private static ClientLevel world;
    private static long ticks;
    private static final class ThunderSound extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        private final float gain;
        ThunderSound(ClientLevel level, Pending p) {
            super(p.sound, p.source, RandomSource.create());
            this.level = level;
            gain = volume = p.volume;
            pitch = p.pitch;
            x = p.position.x; y = p.position.y; z = p.position.z;
        }
        @Override public void tick() {
            if (Minecraft.getInstance().level != level) { stop(); return; }
            volume = WeatherSoundEnvironment.underground() ? 0 : gain;
        }
    }
    private ThunderAudio() {}
    public static int delayTicks(double distance) { return (int)Math.round(distance * 20 / 343); }
    public static float pitchScale(double distance) { return (float)(1 / (1 + distance / 1024)); }
    public static boolean audible() {
        var c = Minecraft.getInstance();
        var entity = c.getCameraEntity();
        return c.level != null && entity != null && !WeatherShelter.underground(c.level,
            c.gameRenderer.mainCamera().position(), entity);
    }
    public static void schedule(ClientLevel level, Vec3 position, SoundEvent sound, SoundSource source,
                                float volume, float pitch) {
        if (world != level) { pending.clear(); world = level; ticks = 0; }
        if (!audible()) return;
        double distance = position.distanceTo(Minecraft.getInstance().gameRenderer.mainCamera().position());
        if (pending.size() >= 256) pending.removeFirst();
        pending.add(new Pending(position, sound, source, volume, pitch * pitchScale(distance), ticks + delayTicks(distance)));
    }
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (world != c.level) { pending.clear(); world = c.level; ticks = 0; }
            if (c.isPaused() || world == null) return;
            ticks++;
            pending.removeIf(p -> {
                if (p.due > ticks) return false;
                if (audible()) c.getSoundManager().play(new ThunderSound(world, p));
                return true;
            });
        });
    }
}
