package naturality.client.particle;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Long-lived, buoyant smoke with smooth, independently phased eddies. */
public final class SmokeParticle extends SingleQuadParticle {
    private static final int TICKS_PER_FRAME = 5;
    private final FabricSpriteSet sprites;
    private final int firstFrame;
    private final double phase;
    private final double curlSpeed;
    private final double riseSpeed;
    private final float initialSize;

    public static void initialize() {
        var registry = ParticleProviderRegistry.getInstance();
        registry.register(ParticleTypes.SMOKE, sprites ->
            (type, level, x, y, z, vx, vy, vz, random) ->
                naturality.config.NaturalityConfig.get().effects.smoke
                ? new SmokeParticle(level, x, y, z, vx, vy, vz, sprites, random, 1.0F)
                : new net.minecraft.client.particle.SmokeParticle.Provider(sprites).createParticle(type, level, x, y, z, vx, vy, vz, random));
        registry.register(ParticleTypes.LARGE_SMOKE, sprites ->
            (type, level, x, y, z, vx, vy, vz, random) ->
                naturality.config.NaturalityConfig.get().effects.smoke
                ? new SmokeParticle(level, x, y, z, vx, vy, vz, sprites, random, 2.5F)
                : new net.minecraft.client.particle.LargeSmokeParticle.Provider(sprites).createParticle(type, level, x, y, z, vx, vy, vz, random));
    }

    private SmokeParticle(ClientLevel level, double x, double y, double z,
                          double vx, double vy, double vz, FabricSpriteSet sprites,
                          RandomSource random, float scale) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        firstFrame = random.nextInt(Math.max(1, sprites.getSprites().size()));
        phase = random.nextDouble() * Math.PI * 2;
        curlSpeed = 0.045 + random.nextDouble() * 0.035;
        riseSpeed = 0.045 + random.nextDouble() * 0.025;
        lifetime = (Math.max(1, sprites.getSprites().size()) - firstFrame) * TICKS_PER_FRAME;
        initialSize = (0.09F + random.nextFloat() * 0.05F) * scale;
        quadSize = initialSize;
        xd = vx + (random.nextDouble() - 0.5) * 0.012;
        yd = vy + riseSpeed * 0.5;
        zd = vz + (random.nextDouble() - 0.5) * 0.012;
        // Keep collision so plumes collect under ceilings instead of crossing them.
        setSize(0.12F, 0.12F);
        rCol = gCol = bCol = 0.65F + random.nextFloat() * 0.2F;
        alpha = 0;
        updateSprite();
    }

    private void updateSprite() {
        var frames = sprites.getSprites();
        if (!frames.isEmpty()) {
            int frame = firstFrame + age / TICKS_PER_FRAME;
            if (frame >= frames.size()) {
                remove();
                return;
            }
            setSprite(frames.get(frame));
        }
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (++age >= lifetime) {
            remove();
            return;
        }
        double t = phase + age * curlSpeed;
        double spread = 0.012 + 0.018 * age / lifetime;
        // Relax toward a changing flow, avoiding frame-to-frame random jitter.
        xd += (spread * (Math.sin(t) + 0.35 * Math.sin(t * 1.73 + phase)) - xd) * 0.075;
        zd += (spread * (Math.cos(t * 0.87) + 0.35 * Math.sin(t * 1.31)) - zd) * 0.075;
        yd += (riseSpeed * (1 + 0.15 * Math.sin(t * 0.63)) - yd) * 0.06;
        move(xd, yd, zd);
        float life = age / (float) lifetime;
        float fade = Mth.clamp((1 - life) / 0.4F, 0, 1);
        alpha = 0.75F * Math.min(age / 8.0F, 1) * fade * fade * (3 - 2 * fade);
        updateSprite();
    }

    @Override
    public float getQuadSize(float partialTick) {
        return initialSize * (1 + 1.6F * Mth.clamp((age + partialTick) / lifetime, 0, 1));
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }
}
