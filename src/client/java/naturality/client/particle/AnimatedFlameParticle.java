package naturality.client.particle;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.RisingParticle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.LightCoordsUtil;

/** An upright, constant-size flame that plays its sprite sequence once. */
public final class AnimatedFlameParticle extends RisingParticle {
    private static final int TICKS_PER_FRAME = 5;
    private static final FacingCameraMode WORLD_UP = (rotation, camera, _) ->
        rotation.rotationY(2 * (float) Math.atan2(camera.rotation().y, camera.rotation().w));
    private final FabricSpriteSet sprites;

    public static void initialize() {
        var registry = ParticleProviderRegistry.getInstance();
        registry.register(ParticleTypes.FLAME, sprites ->
            (type, level, x, y, z, vx, vy, vz, random) ->
                naturality.config.NaturalityConfig.get().effects.flames
                ? new AnimatedFlameParticle(level, x, y, z, vx, vy, vz, sprites, 1)
                : new net.minecraft.client.particle.FlameParticle.Provider(sprites).createParticle(type, level, x, y, z, vx, vy, vz, random));
        registry.register(ParticleTypes.SOUL_FIRE_FLAME, sprites ->
            (type, level, x, y, z, vx, vy, vz, random) ->
                naturality.config.NaturalityConfig.get().effects.flames
                ? new AnimatedFlameParticle(level, x, y, z, vx, vy, vz, sprites, 1)
                : new net.minecraft.client.particle.FlameParticle.Provider(sprites).createParticle(type, level, x, y, z, vx, vy, vz, random));
        registry.register(ParticleTypes.SMALL_FLAME, sprites ->
            (type, level, x, y, z, vx, vy, vz, random) ->
                naturality.config.NaturalityConfig.get().effects.flames
                ? new AnimatedFlameParticle(level, x, y, z, vx, vy, vz, sprites, 0.5F)
                : new net.minecraft.client.particle.FlameParticle.SmallFlameProvider(sprites).createParticle(type, level, x, y, z, vx, vy, vz, random));
    }

    private AnimatedFlameParticle(ClientLevel level, double x, double y, double z,
                                  double vx, double vy, double vz, FabricSpriteSet sprites, float size) {
        super(level, x, y, z, vx, vy, vz, sprites.first());
        this.sprites = sprites;
        scale(size);
        lifetime = Math.max(1, sprites.getSprites().size()) * TICKS_PER_FRAME;
    }

    @Override public FacingCameraMode getFacingCameraMode() { return WORLD_UP; }

    @Override public void tick() {
        super.tick();
        var frames = sprites.getSprites();
        int frame = age / TICKS_PER_FRAME;
        if (age >= lifetime || frame >= frames.size()) {
            remove();
        } else {
            setSprite(frames.get(frame));
        }
    }

    // Preserve vanilla flame motion, including its lack of block collision.
    @Override public void move(double x, double y, double z) {
        setBoundingBox(getBoundingBox().move(x, y, z));
        setLocationFromBoundingbox();
    }

    @Override protected int getLightCoords(float partialTick) {
        return LightCoordsUtil.addSmoothBlockEmission(super.getLightCoords(partialTick),
            (age + partialTick) / lifetime);
    }

    @Override protected Layer getLayer() { return Layer.OPAQUE; }
}
