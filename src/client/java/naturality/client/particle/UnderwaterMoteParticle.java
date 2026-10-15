package naturality.client.particle;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;

/** Slowly drifting underwater specks with a soft entrance and exit. */
public final class UnderwaterMoteParticle extends SingleQuadParticle {
    private static final int FADE_TICKS = 20;
    private final double phase;

    public static void initialize() {
        ParticleProviderRegistry.getInstance().register(ParticleTypes.UNDERWATER, sprites ->
            (_, level, x, y, z, _, _, _, random) -> {
                // Longer lives would increase density without a lower spawn rate.
                if (random.nextFloat() >= 0.2F) return null;
                var particle = new UnderwaterMoteParticle(level, x, y, z, sprites.first(), random);
                WindParticleControl.mark(particle, true);
                return particle;
            });
    }

    private UnderwaterMoteParticle(ClientLevel level, double x, double y, double z,
                                   TextureAtlasSprite sprite, RandomSource random) {
        super(level, x, y - 0.125, z, sprite);
        quadSize *= 0.2F + random.nextFloat() * 0.6F;
        setSize(0.01F, 0.01F);
        hasPhysics = false;
        lifetime = 100 + random.nextInt(41);
        phase = random.nextDouble() * Math.PI * 2;
        alpha = 0;
    }

    private boolean isInWater(double x, double y, double z) {
        var pos = BlockPos.containing(x, y, z);
        var fluid = level.getFluidState(pos);
        return fluid.is(FluidTags.WATER) && y < pos.getY() + fluid.getHeight(level, pos);
    }

    @Override public void tick() {
        xo = x; yo = y; zo = z;
        if (++age >= lifetime) { remove(); return; }
        if (!isInWater(x, y, z)) {
            lifetime = Math.min(lifetime, age + FADE_TICKS);
            return;
        }
        double time = age * 0.035;
        xd += (Math.sin(phase + time) * 0.003 - xd) * 0.08;
        yd += (Math.sin(phase * 1.7 + time * 0.6) * 0.0015 - yd) * 0.08;
        zd += (Math.cos(phase + time * 0.8) * 0.003 - zd) * 0.08;
        // Keep motes submerged and out of dry neighboring blocks.
        if (isInWater(x + xd, y + yd, z + zd)) move(xd, yd, zd);
    }

    private static float smooth(float value) {
        float t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    @Override public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        float time = age + partialTick;
        alpha = 0.7F * smooth(time / FADE_TICKS) * smooth((lifetime - time) / FADE_TICKS);
        super.extract(state, camera, partialTick);
    }

    @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
}
