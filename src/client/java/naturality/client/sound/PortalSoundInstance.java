package naturality.client.sound;

import naturality.config.NaturalityConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;

/** Retains vanilla audio, pitch and positioning while managing its lifetime and gain. */
public final class PortalSoundInstance implements TickableSoundInstance {
    private final SoundInstance original;
    private final @org.jspecify.annotations.Nullable ClientLevel level = Minecraft.getInstance().level;
    private final boolean ambient, trigger;
    private boolean stopped;
    private int fadeTicks;
    private boolean fading;

    public static SoundInstance wrap(SoundInstance sound) {
        if (sound instanceof PortalSoundInstance) return sound;
        String id = sound.getIdentifier().toString();
        return switch (id) {
            case "naturality:portal_open", "minecraft:block.portal.ambient",
                 "minecraft:block.portal.trigger", "minecraft:block.portal.travel" -> new PortalSoundInstance(sound);
            default -> sound;
        };
    }

    private PortalSoundInstance(SoundInstance original) {
        this.original = original;
        ambient = original.getIdentifier().getPath().equals("block.portal.ambient");
        trigger = original.getIdentifier().getPath().equals("block.portal.trigger");
    }

    @Override public void tick() {
        var client = Minecraft.getInstance();
        var level = this.level;
        if (client.level != level || level == null) { stopped = true; return; }
        if (ambient && !level.getBlockState(BlockPos.containing(getX(), getY(), getZ())).is(Blocks.NETHER_PORTAL)) {
            stopped = true;
        }
        if (trigger) {
            var player = client.player;
            // Test actual contact, not the lingering portal overlay after stepping out.
            boolean touching = player != null && BlockPos.betweenClosedStream(player.getBoundingBox().deflate(0.001))
                .anyMatch(pos -> level.getBlockState(pos).is(Blocks.NETHER_PORTAL));
            if (!touching) fading = true;
            if (fading && ++fadeTicks >= 5) stopped = true;
        }
    }

    @Override public float getVolume() {
        var config = NaturalityConfig.get().portalChanges;
        double gain = ambient ? config.ambientVolume : getIdentifier().getNamespace().equals("naturality")
            ? config.openingVolume : config.travelVolume;
        return original.getVolume() * (float) NaturalityConfig.soundVolume(gain) * Math.max(0, 1 - fadeTicks / 5.0F);
    }
    @Override public boolean isStopped() { return stopped; }
    @Override public Identifier getIdentifier() { return original.getIdentifier(); }
    @Override public @org.jspecify.annotations.Nullable WeighedSoundEvents getOrResolve(SoundManager manager) { return original.getOrResolve(manager); }
    @Override public @org.jspecify.annotations.Nullable Sound getSound() { return original.getSound(); }
    @Override public @org.jspecify.annotations.Nullable WeighedSoundEvents getSoundEvent() { return original.getSoundEvent(); }
    @Override public SoundSource getSource() { return original.getSource(); }
    @Override public boolean isLooping() { return original.isLooping(); }
    @Override public boolean isRelative() { return original.isRelative(); }
    @Override public int getDelay() { return original.getDelay(); }
    @Override public float getPitch() { return original.getPitch(); }
    @Override public double getX() { return original.getX(); }
    @Override public double getY() { return original.getY(); }
    @Override public double getZ() { return original.getZ(); }
    @Override public Attenuation getAttenuation() { return original.getAttenuation(); }
    @Override public boolean canStartSilent() { return original.canStartSilent(); }
    @Override public boolean canPlaySound() { return !stopped && original.canPlaySound(); }
}
