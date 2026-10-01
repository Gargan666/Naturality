package naturality.client.particle;

import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.util.Mth;
import net.minecraft.util.ARGB;
import org.joml.Quaternionf;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A two-sided, velocity-facing square drawn from one actual portal palette color. */
public final class PortalMoteParticle extends SingleQuadParticle {
    private static final ParticleLimit LIMIT = new ParticleLimit(512);
    private final BlockPos portal;
    private final Direction.Axis axis;
    private final Vec3 start;
    private final Vec3 target;
    private final Vec3 control;
    private final double initialDistance;
    private Vec3 velocity;

    public PortalMoteParticle(ClientLevel level, BlockPos portal, Direction.Axis axis,
                              double x, double y, double z, double vx, double vy, double vz,
                              TextureAtlasSprite sprite, PortalGlowAppearance appearance, RandomSource random) {
        // Vanilla portal velocities describe an initial offset away from the portal.
        // Start there immediately, avoiding vanilla's first-tick position jump.
        super(level, x + vx, y + vy + 0.5, z + vz, sprite);
        this.portal = portal.immutable();
        this.axis = axis;
        double normalCoordinate = axis == Direction.Axis.X ? this.z - portal.getZ() - 0.5
            : this.x - portal.getX() - 0.5;
        double spawnOffset = PortalMoteGeometry.spawnOffset(normalCoordinate);
        setPos(axis == Direction.Axis.Z ? portal.getX() + 0.5 + spawnOffset : this.x,
            this.y, axis == Direction.Axis.X ? portal.getZ() + 0.5 + spawnOffset : this.z);
        xo = this.x;
        yo = this.y;
        zo = this.z;
        start = new Vec3(this.x, this.y, this.z);
        double surface = Math.copySign(PortalGlowGeometry.PORTAL_SURFACE, normalCoordinate);
        target = new Vec3(axis == Direction.Axis.X ? Mth.clamp(x, portal.getX() + 0.05, portal.getX() + 0.95)
                : portal.getX() + 0.5 + surface,
            Mth.clamp(y, portal.getY() + 0.05, portal.getY() + 0.95),
            axis == Direction.Axis.Z ? Mth.clamp(z, portal.getZ() + 0.05, portal.getZ() + 0.95)
                : portal.getZ() + 0.5 + surface);
        control = start.lerp(target, 0.5).add(0, 0.2, 0);
        velocity = control.subtract(start);
        initialDistance = planeDistance(start);
        lifetime = 30 + random.nextInt(16);
        quadSize = (1 + random.nextInt(2)) / 32.0F; // Maximum side: one or two texture pixels.
        alpha = appearance.opacity();
        hasPhysics = false;
    }

    private double planeDistance(Vec3 position) {
        return Math.abs(axis == Direction.Axis.X ? position.z - target.z : position.x - target.x);
    }

    @Override
    public void tick() {
        if (!naturality.config.NaturalityConfig.get().portalChanges.portalParticleChanges) { remove(); return; }
        if (!naturality.util.LoadedChunks.has(level, portal)) {
            remove();
            return;
        }
        BlockState state = level.getBlockState(portal);
        if (!state.is(Blocks.NETHER_PORTAL)
            || state.getValue(NetherPortalBlock.AXIS) != axis || age >= lifetime) {
            remove();
            return;
        }
        xo = x;
        yo = y;
        zo = z;
        double t = ++age / (double) lifetime;
        Vec3 next = start.scale((1 - t) * (1 - t))
            .add(control.scale(2 * (1 - t) * t)).add(target.scale(t * t));
        velocity = next.subtract(new Vec3(x, y, z));
        setPos(next.x, next.y, next.z);
    }

    @Override
    public float getQuadSize(float partialTick) {
        if (age == 0) return 0;
        Vec3 position = new Vec3(Mth.lerp(partialTick, xo, x), Mth.lerp(partialTick, yo, y),
            Mth.lerp(partialTick, zo, z));
        return quadSize * PortalMoteGeometry.growth(planeDistance(position), initialDistance);
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        Vec3 position = new Vec3(Mth.lerp(partialTick, xo, x), Mth.lerp(partialTick, yo, y),
            Mth.lerp(partialTick, zo, z));
        double distance = planeDistance(position);
        float proximity = PortalMoteGeometry.growth(distance, initialDistance);
        float opacity = alpha * PortalMoteGeometry.arrivalAlpha(distance);
        float size = age == 0 ? 0 : quadSize * proximity;
        if (size <= 0 || opacity <= 0) return;
        Quaternionf rotation = PortalMoteGeometry.orientation(velocity, camera.position().subtract(position));
        Vec3 relative = position.subtract(camera.position());
        float u = PortalMoteGeometry.paletteU(proximity);
        float halfV = 0.5F / PortalMoteGeometry.crossWidth(proximity);
        // The shader clips the transverse width; texture X remains the travel axis.
        state.add(getLayer(), (float) relative.x, (float) relative.y, (float) relative.z,
            rotation.x, rotation.y, rotation.z, rotation.w, size,
            u, u, -halfV, halfV, ARGB.colorFromFloat(opacity,
                1 - naturality.client.portal.PortalOpeningClient.pulse(portal, partialTick), 1, 1), 0xF000F0);
    }

    @Override protected int getLightCoords(float partialTick) { return 0xF000F0; }
    @Override protected Layer getLayer() { return PortalGlowRenderLayer.MOTE_LAYER; }
    @Override public Optional<ParticleLimit> getParticleLimit() { return Optional.of(LIMIT); }
}
