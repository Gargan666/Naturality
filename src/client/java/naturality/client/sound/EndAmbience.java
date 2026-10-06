package naturality.client.sound;

import naturality.NaturalitySounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/** Sparse, per-listener End ambience, independent of terrain and sky events. */
public final class EndAmbience {
    private static final RandomSource RANDOM = RandomSource.create();
    private static ClientLevel world;
    private static AmbienceSound playing;
    private static int remaining;
    // Retain across dimension changes so returning to the End cannot repeat the last clip.
    private static int lastClip = -1;

    private EndAmbience() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(EndAmbience::tick);
    }

    private static int nextDelay() {
        return 40 + RANDOM.nextInt(1161);
    }

    private static void tick(Minecraft client) {
        ClientLevel end = client.level != null && client.level.dimension().equals(Level.END)
            && client.player != null ? client.level : null;
        if (world != end) {
            if (playing != null) client.getSoundManager().stop(playing);
            playing = null;
            world = end;
            remaining = nextDelay();
        }
        if (world == null || client.isPaused()) return;
        if (playing != null) {
            if (client.getSoundManager().isActive(playing)) return;
            playing = null;
        }
        if (--remaining > 0) return;
        int clipCount = NaturalitySounds.END_AMBIENCE_CLIPS.size();
        int clip = RANDOM.nextInt(lastClip < 0 ? clipCount : clipCount - 1);
        if (lastClip >= 0 && clip >= lastClip) clip++;
        lastClip = clip;
        playing = new AmbienceSound(world, clip);
        client.getSoundManager().play(playing);
        remaining = nextDelay();
    }

    private static final class AmbienceSound extends AbstractTickableSoundInstance {
        private final ClientLevel level;

        AmbienceSound(ClientLevel level, int clip) {
            super(NaturalitySounds.END_AMBIENCE_CLIPS.get(clip), SoundSource.AMBIENT, RandomSource.create());
            this.level = level;
            volume = 1;
            pitch = 1;
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override public void tick() {
            if (Minecraft.getInstance().level != level || Minecraft.getInstance().player == null) stop();
        }
    }
}
