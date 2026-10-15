package naturality.client.audio.ambience.caves;

import naturality.bootstrap.ContentRegistration.NaturalitySounds;
import naturality.config.startup.FeatureConfig.Feature;
import naturality.api.worlds.LoadedChunks;
import naturality.systems.weather.framework.WeatherSystem.WeatherShelter;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** One streamed loop, fading with the listener's local underground room size. */
@org.jspecify.annotations.NullMarked
public final class CaveAmbience {
    private static @org.jspecify.annotations.Nullable ClientLevel world;
    private static @org.jspecify.annotations.Nullable CaveLoop loop;
    private static boolean large;
    private static int sampleTicks;
    private static float gain;

    private CaveAmbience() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(CaveAmbience::tick);
    }

    /** Current fade gain before Minecraft's category volume and audio effects. */
    public static float gain() { return gain; }

    public static boolean isLargeCave(Level level, Vec3 eye, Entity listener) {
        if (!level.dimension().equals(Level.OVERWORLD)
                || !LoadedChunks.has(level, BlockPos.containing(eye))
                || !level.getFluidState(BlockPos.containing(eye)).isEmpty()
                || !WeatherShelter.underground(level, eye, listener)) return false;
        if (clearance(level, eye, new Vec3(0, 1, 0), 4, listener) < 4) return false;
        int broadAxes = 0;
        // Opposing rays measure room width, tolerating a listener near one wall.
        // Requiring three axes rejects long, narrow corridors.
        for (int axis = 0; axis < 4; axis++) {
            double angle = axis * Math.PI / 4;
            Vec3 direction = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            double width = clearance(level, eye, direction, 16, listener)
                + clearance(level, eye, direction.scale(-1), 16, listener);
            if (width >= 16) broadAxes++;
        }
        return broadAxes >= 3;
    }

    private static double clearance(Level level, Vec3 eye, Vec3 direction, int reach, Entity listener) {
        // Check the entire ray before clipping: absent chunks never count as open space.
        for (int step = 0; step <= reach; step++) {
            if (!LoadedChunks.has(level, BlockPos.containing(eye.add(direction.scale(step))))) return 0;
        }
        Vec3 end = eye.add(direction.scale(reach));
        var hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, listener));
        return hit.getType() == HitResult.Type.MISS ? reach : hit.getLocation().distanceTo(eye);
    }

    private static void tick(Minecraft client) {
        var level = client.level;
        var currentLoop = loop;
        if (world != level) {
            if (currentLoop != null) client.getSoundManager().stop(currentLoop);
            loop = null;
            currentLoop = null;
            world = level;
            gain = 0;
            large = false;
            sampleTicks = 0;
        }
        if (level == null || client.isPaused()) return;
        var listener = client.getCameraEntity();
        if (!Feature.VISUAL_OVERHAUL.enabled() || listener == null
                || !level.dimension().equals(Level.OVERWORLD)) large = false;
        else if (sampleTicks-- <= 0) {
            large = isLargeCave(level, listener.getEyePosition(), listener);
            sampleTicks = 9;
        }
        gain = Math.clamp(gain + (large ? 1F : -1F) / 60F, 0F, 1F);
        if (gain == 0) {
            if (currentLoop != null) client.getSoundManager().stop(currentLoop);
            loop = null;
        } else if (currentLoop == null || !client.getSoundManager().isActive(currentLoop)) {
            currentLoop = new CaveLoop(level);
            loop = currentLoop;
            client.getSoundManager().play(currentLoop);
        }
    }

    private static final class CaveLoop extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        CaveLoop(ClientLevel level) {
            super(NaturalitySounds.CAVE_AMBIENCE, SoundSource.AMBIENT, RandomSource.create());
            this.level = level;
            looping = true;
            delay = 0;
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
            volume = gain;
            pitch = 1;
        }
        @Override public boolean canStartSilent() { return true; }
        @Override public void tick() {
            if (Minecraft.getInstance().level != level) { stop(); return; }
            volume = gain;
        }
    }
}
