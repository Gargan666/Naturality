package naturality.client.particle;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

/** A stationary horizontal explosion with a smaller, delayed camera-facing copy. */
public final class FlatExplosionParticle extends SingleQuadParticle {
    private static final int TICKS_PER_FRAME = 2;
    private static final float CAMERA_COPY_SCALE = 0.8F;
    private final FabricSpriteSet sprites;
    private final int cameraStartFrame;
    private final int horizontalLifetime;
    private final Direction surfaceNormal;

    public static void initialize() {
        var registry = ParticleProviderRegistry.getInstance();
        registry.register(ParticleTypes.EXPLOSION, sprites ->
            (type, level, x, y, z, vx, vy, vz, random) -> create(level, x, y, z, sprites, vx, vy));
        registry.register(ParticleTypes.EXPLOSION_EMITTER, sprites ->
            (type, level, x, y, z, vx, vy, vz, random) -> create(level, x, y, z, sprites, vx, vy));
    }

    private static FlatExplosionParticle create(ClientLevel level, double x, double y, double z,
                                                FabricSpriteSet sprites, double sizeScale, double face) {
        Direction normal = face >= 1 && face <= 6 && face == Math.rint(face)
            ? Direction.from3DDataValue((int) face - 1) : Direction.UP;
        var particle = new FlatExplosionParticle(level, x, y, z, sprites, sizeScale, normal);
        WindParticleControl.mark(particle, true);
        return particle;
    }

    private FlatExplosionParticle(ClientLevel level, double x, double y, double z, FabricSpriteSet sprites,
                                  double sizeScale, Direction surfaceNormal) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.surfaceNormal = surfaceNormal;
        hasPhysics = false;
        // Explosion packets supply their adjusted size through the unused X velocity.
        // Direct particle spawns with zero velocity retain the base size.
        quadSize = 6.0F * (float) (Double.isFinite(sizeScale) && sizeScale > 0 ? sizeScale : 1);
        int frameCount = Math.max(1, sprites.getSprites().size());
        cameraStartFrame = Math.min(2, frameCount - 1);
        horizontalLifetime = frameCount * TICKS_PER_FRAME;
        lifetime = Math.max(horizontalLifetime, (frameCount - cameraStartFrame + 1) * TICKS_PER_FRAME);
        // Include the vertical camera-facing copy in the visibility bounds.
        double radius = quadSize * Math.sqrt(2);
        setBoundingBox(new AABB(x - radius, y - radius, z - radius,
            x + radius, y + radius, z + radius));
    }

    @Override public void tick() {
        if (++age >= lifetime) {
            remove();
            return;
        }
        var frames = sprites.getSprites();
        setSprite(frames.get(Math.min(frames.size() - 1, age / TICKS_PER_FRAME)));
    }

    @Override public void move(double x, double y, double z) {
        // External wind and other movement hooks cannot displace the effect.
    }

    @Override public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        var eye = camera.position();
        if (age < horizontalLifetime) {
            var rotation = new Quaternionf().rotationTo(0, 0, 1,
                surfaceNormal.getStepX(), surfaceNormal.getStepY(), surfaceNormal.getStepZ());
            float renderX = (float) (x + surfaceNormal.getStepX() / 512.0 - eye.x);
            float renderY = (float) (y + surfaceNormal.getStepY() / 512.0 - eye.y);
            float renderZ = (float) (z + surfaceNormal.getStepZ() / 512.0 - eye.z);
            extractRotatedQuad(state, rotation, renderX, renderY, renderZ, partialTick);
            rotation.rotateY((float) Math.PI);
            extractRotatedQuad(state, rotation, renderX, renderY, renderZ, partialTick);
        }
        // Begin on the third sprite, holding it for four ticks before advancing.
        int copyFrame = cameraStartFrame + Math.max(0, age - TICKS_PER_FRAME) / TICKS_PER_FRAME;
        if (copyFrame >= sprites.getSprites().size()) return;
        var copySprite = sprites.getSprites().get(copyFrame);
        var rotation = new Quaternionf();
        getFacingCameraMode().setRotation(rotation, camera, partialTick);
        state.add(getLayer(), (float) (x - eye.x), (float) (y - eye.y), (float) (z - eye.z),
            rotation.x, rotation.y, rotation.z, rotation.w, quadSize * CAMERA_COPY_SCALE,
            copySprite.getU0(), copySprite.getU1(), copySprite.getV0(), copySprite.getV1(),
            0xFFFFFFFF, getLightCoords(partialTick));
    }

    @Override protected int getLightCoords(float partialTick) { return 0xF000F0; }
    @Override protected Layer getLayer() { return Layer.OPAQUE; }
}
