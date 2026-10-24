package naturality.client.particles.sparks;

import naturality.bootstrap.ContentRegistration.NaturalityParticles;
import naturality.client.api.geometry.ParticleGeometry;
import naturality.client.systems.weather.wind.WindRendering.WindParticleControl;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.jspecify.annotations.NullMarked;

/** The authorized thin spark sprite follows velocity, with camera-facing roll only. */
@NullMarked
public final class SparkParticle extends SingleQuadParticle {
    private final Matrix3f basis = new Matrix3f();
    private final Quaternionf rotation = new Quaternionf();

    public static void initialize() {
        ParticleProviderRegistry.getInstance().register(NaturalityParticles.SWORD_COUNTER_SPARK, sprites ->
            (_, level, x, y, z, vx, vy, vz, _) ->
                new SparkParticle(level, x, y, z, vx, vy, vz, sprites.first()));
    }

    private SparkParticle(ClientLevel level, double x, double y, double z,
            double vx, double vy, double vz, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        setParticleSpeed(vx, vy, vz);
        setSize(0.015F, 0.015F);
        quadSize = 0.09F + random.nextFloat() * 0.05F;
        lifetime = 10 + random.nextInt(9);
        gravity = 0.75F;
        friction = 0.96F;
        WindParticleControl.mark(this, true);
        if (!level.noCollision(getBoundingBox())) remove();
    }

    @Override public void move(double dx, double dy, double dz) {
        double oldX = x, oldY = y, oldZ = z;
        super.move(dx, dy, dz);
        // Particle's normal collision response stops or slides; sparks vanish on every blocked axis.
        if (Math.abs(x - oldX - dx) > 1.0E-7 || Math.abs(y - oldY - dy) > 1.0E-7
                || Math.abs(z - oldZ - dz) > 1.0E-7) remove();
    }

    @Override public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        if (!isAlive()) return;
        double renderX = Mth.lerp(partialTick, xo, x) - camera.position().x;
        double renderY = Mth.lerp(partialTick, yo, y) - camera.position().y;
        double renderZ = Mth.lerp(partialTick, zo, z) - camera.position().z;
        ParticleGeometry.orientation(xd, yd, zd, -renderX, -renderY, -renderZ, basis, rotation);
        float progress = Mth.clamp((age + partialTick) / lifetime, 0, 1);
        float cooling = Math.min(1, progress * 1.6F);
        rCol = 1;
        gCol = Mth.lerp(cooling, 1, 0.35F);
        bCol = Mth.lerp(cooling, 0.9F, 0.02F);
        alpha = 1 - progress * progress;
        extractRotatedQuad(state, rotation, (float) renderX, (float) renderY, (float) renderZ, partialTick);
    }

    @Override protected int getLightCoords(float partialTick) { return 0xF000F0; }
    @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
}
