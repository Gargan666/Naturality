package naturality.client.effects.ambienthorror;

import java.util.Set;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.state.level.*;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Two depth-tested emissive squares, with no entity or particle debug bounds. */
public final class HorrorEyesLayer {
    private static final DepthStencilState DEPTH = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);
    private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
        .withLocation(Naturality.id("pipeline/horror_eyes")).withCull(false)
        .withDepthStencilState(DEPTH).withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
    private static final OitPipelineSet OIT = RenderPipelines.register(OitPipelineSet.builder("naturality_horror_eyes",
        RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET).withCull(false))
        .withDepthBoundsModifier(b -> b.withDepthStencilState(DEPTH))
        .withTransmittanceModifier(b -> b.withDepthStencilState(DEPTH))
        .withAccumulateModifier(b -> b.withDepthStencilState(DEPTH)).build());
    private static final SingleQuadParticle.Layer LAYER = new SingleQuadParticle.Layer(true,
        Naturality.id("textures/particle/horror_eyes.png"), PIPELINE, OIT);
    public static void initialize() {}
    public static void extract(ParticlesRenderState output, Camera camera, Vec3 position, float alpha, EyeVariant variant) {
        Vec3 center = position.subtract(camera.position());
        if (alpha > 0 && center.lengthSqr() > .000001) {
            Vector3f normal = center.toVector3f().normalize();
            Vector3f reference = Math.abs(normal.y) > .999F ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
            Vector3f right = new Vector3f(normal).cross(reference).normalize();
            Vector3f up = new Vector3f(right).cross(normal).normalize();
            output.add(new Eyes(center, right, up, alpha, variant));
        }
    }
    private static final class Eyes extends QuadParticleRenderState {
        private final Vec3 center;
        private final Vector3f right, up;
        private final float alpha;
        private final EyeVariant variant;
        Eyes(Vec3 center, Vector3f right, Vector3f up, float alpha, EyeVariant variant) {
            this.center = center; this.right = right; this.up = up; this.alpha = alpha;
            this.variant = variant;
        }
        public Set<SingleQuadParticle.Layer> layers() { return Set.of(LAYER); }
        public boolean isEmpty() { return false; }
        public void clear() {}
        public void submit(net.minecraft.client.renderer.SubmitNodeCollector collector, CameraRenderState camera) {
            collector.submitQuadParticleGroup(this);
        }
        public void buildLayer(SingleQuadParticle.Layer layer, VertexConsumer buffer) {
            if (layer != LAYER) return;
            for (int eye = -1; eye <= 1; eye += 2) for (int i = 0; i < 4; i++) {
                float half = variant.size * .5F;
                float x = (eye < 0 ? variant.leftX : variant.rightX) + ((i == 0 || i == 1) ? -half : half);
                float y = (eye < 0 ? variant.leftY : variant.rightY) + ((i == 1 || i == 2) ? half : -half);
                buffer.addVertex((float)center.x + right.x * x + up.x * y,
                    (float)center.y + right.y * x + up.y * y, (float)center.z + right.z * x + up.z * y)
                    // One white texel per eye in a three-pixel white/clear/white texture.
                    // Sample texel centers so filtering cannot soften the square's edges.
                    .setUv(eye < 0 ? 1F / 6F : 5F / 6F, .5F)
                    .setColor(ARGB.colorFromFloat(alpha, 1, 1, 1)).setLight(0xF000F0);
            }
        }
    }
    private HorrorEyesLayer() {}

    /** Every dot still samples exactly one white texture pixel. */
    public enum EyeVariant {
        NORMAL(.09F, -.14F, 0, .14F, 0),
        UNEVEN(.09F, -.16F, -.055F, .12F, .055F),
        CROOKED(.09F, -.10F, .065F, .20F, -.025F),
        SMALL_WIDE(.045F, -.26F, 0, .26F, 0);

        public final float size, leftX, leftY, rightX, rightY;
        EyeVariant(float size, float leftX, float leftY, float rightX, float rightY) {
            this.size = size; this.leftX = leftX; this.leftY = leftY; this.rightX = rightX; this.rightY = rightY;
        }
    }
}
