package naturality.client.particle;

import naturality.NaturalityParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.ParticleTypes;
import org.joml.Quaternionf;

/** Animated translucent smoke; lingering clouds alone lie in the world X/Z plane. */
public final class FlatPotionSmokeParticle extends SingleQuadParticle {
    private final SpriteSet sprites;
    private final boolean flat;

    public static void initialize() {
        var registry = ParticleProviderRegistry.getInstance();
        // Cloud-only provider: its auxiliary values carry the synchronized potion RGB.
        registry.register(NaturalityParticles.POTION_CLOUD, sprites ->
            (_, level, x, y, z, red, green, blue, random) -> {
                var puff = new FlatPotionSmokeParticle(level, x, y + 0.015 + random.nextDouble() * 0.12, z, 0, 0, 0, sprites, true);
                puff.setColor((float) red, (float) green, (float) blue);
                return puff;
            });
        registry.register(ParticleTypes.DRAGON_BREATH, sprites ->
            (type, level, x, y, z, vx, vy, vz, random) -> {
                var puff = new FlatPotionSmokeParticle(level, x, y, z, vx, vy, vz, sprites, false);
                puff.setColor(0.7176471F + random.nextFloat() * (0.8745098F - 0.7176471F),
                    0, 0.8235294F + random.nextFloat() * (0.9764706F - 0.8235294F));
                puff.setPower(type.getPower());
                return puff;
            });
    }

    private FlatPotionSmokeParticle(ClientLevel level, double x, double y, double z,
                                   double vx, double vy, double vz, SpriteSet sprites, boolean flat) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.flat = flat;
        xd = vx;
        yd = vy;
        zd = vz;
        friction = 0.96F;
        hasPhysics = false;
        quadSize = 0.3F + random.nextFloat() * 0.15F;
        lifetime = 24 + random.nextInt(17);
        alpha = 0.35F;
        setSpriteFromAge(sprites);
    }

    @Override public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
        alpha = 0.35F * Math.min(1, (lifetime - age) / 8.0F);
    }

    @Override public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        if (!flat) {
            super.extract(state, camera, partialTick);
            return;
        }
        var rotation = new Quaternionf().rotationTo(0, 0, 1, 0, 1, 0);
        extractRotatedQuad(state, camera, rotation, partialTick);
        rotation.rotateY((float) Math.PI);
        extractRotatedQuad(state, camera, rotation, partialTick);
    }

    @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
}
