package naturality.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A stationary edge strip. Back-face rejection affects rendering, never its lifetime. */
public final class PortalGlowParticle extends SingleQuadParticle {
    private final Direction inward;
    private final Quaternionf rotation;
    private final Vector3f depth;
    private final Vector3f tangent;
    private final BlockPos portalBlock;
    private final Direction.Axis axis;
    private final PortalGlowAppearance appearance;
    private long lastTick;

    public PortalGlowParticle(ClientLevel level, BlockPos portalBlock, Direction.Axis axis,
                              Direction outward, TextureAtlasSprite sprite, PortalGlowAppearance appearance) {
        super(level,
            portalBlock.getX() + 0.5 + outward.getStepX() * (0.5 - PortalGlowGeometry.INSET),
            portalBlock.getY() + 0.5 + outward.getStepY() * (0.5 - PortalGlowGeometry.INSET),
            portalBlock.getZ() + 0.5 + outward.getStepZ() * (0.5 - PortalGlowGeometry.INSET), sprite);
        inward = outward.getOpposite();
        this.portalBlock = portalBlock.immutable();
        this.axis = axis;
        this.appearance = appearance;
        rotation = PortalGlowGeometry.rotation(axis, inward);
        depth = PortalGlowGeometry.depth(axis);
        tangent = new Vector3f(depth).cross(new Vector3f(inward.getStepX(), inward.getStepY(), inward.getStepZ()));
        lastTick = level.getGameTime();
        hasPhysics = false;
        quadSize = 0.5F;
        // Intro glow appears at full opacity; its reach, rather than opacity, settles.
        if (naturality.client.portal.PortalOpeningClient.hasIntro(portalBlock)) age = 10;
        // Fixed extra tiles accommodate the pulse without stretching the pixel grid.
        setBoundingBox(new AABB(x - 2.13, y - 2.13, z - 2.13, x + 2.13, y + 2.13, z + 2.13));
    }

    @Override
    public void tick() {
        lastTick = level.getGameTime();
        if (age < 10) {
            age++;
        }
        // The controller owns removal. No movement, random expiry, or gravity.
    }

    public boolean needsReplacement(long gameTime) {
        // ParticleEngine can clear/evict a particle without calling remove().
        return !isAlive() || gameTime - lastTick > 2;
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        Vec3 eye = camera.position();
        if (!PortalGlowGeometry.isFront(eye.x, eye.y, eye.z, x, y, z, inward)) {
            return;
        }
        int side = PortalGlowGeometry.visibleDepthSide((eye.x - x) * depth.x + (eye.z - z) * depth.z);
        if (side == 0) return;
        float fadeIn = PortalGlowGeometry.expoOut((age + partialTick) / 10.0F);
        float pulse = naturality.client.portal.PortalOpeningClient.pulse(portalBlock, partialTick);
        int color = ARGB.colorFromFloat(fadeIn * appearance.opacity(), 1 - pulse, 1, 1);
        int light = 0xF000F0;
        // Only trim real corners; adjoining collinear frame tiles stay joined.
        boolean horizontal = inward.getAxis() == Direction.Axis.Y;
        Direction along = horizontal ? (axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH) : Direction.UP;
        double trimStart = isPortal(portalBlock.relative(along.getOpposite())) ? 0 : PortalGlowGeometry.INSET;
        double trimEnd = isPortal(portalBlock.relative(along)) ? 0 : PortalGlowGeometry.INSET;
        if (naturality.client.portal.PortalCrossingClient.clipFrame(portalBlock, axis, inward,
                x, y, z, side, eye, color, pulse, trimStart, trimEnd)) return;

        // This layer interprets UVs as edge position and distance from the portal.
        // Shader slicing gives 16 independently waving strips without 32 extra quads.
        // Wrap by the wave's 1.5-block wavelength to retain precision far from spawn.
        double edgePosition = x * tangent.x + y * tangent.y + z * tangent.z;
        float edgeStart = (float) ((edgePosition - 0.5) % 1.5);
        // Only the camera's side of the portal slab is visible. The far frame
        // edge remains visible; the glow behind the portal surface does not.
        submitHalf(state, eye, side * 0.5F, edgeStart, edgeStart + 1,
            side < 0 ? 0 : 1, side < 0 ? 1 : 0, color, light);
        if (pulse > 0) {
            submitHalf(state, eye, side * 1.5F, edgeStart, edgeStart + 1,
                side < 0 ? 1 : 2, side < 0 ? 2 : 1, color, light);
        }
    }

    private boolean isPortal(BlockPos pos) {
        if (!naturality.util.LoadedChunks.has(level, pos)) return false;
        var block = level.getBlockState(pos);
        return block.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
            && block.getValue(net.minecraft.world.level.block.NetherPortalBlock.AXIS) == axis;
    }

    private void submitHalf(QuadParticleRenderState state, Vec3 eye, float offset,
                            float u0, float u1, float v0, float v1, int color, int light) {
        state.add(getLayer(),
            (float) (x - eye.x + depth.x * offset),
            (float) (y - eye.y + depth.y * offset),
            (float) (z - eye.z + depth.z * offset),
            rotation.x, rotation.y, rotation.z, rotation.w,
            quadSize, u0, u1, v0, v1, color, light);
    }

    @Override
    protected Layer getLayer() {
        return PortalGlowRenderLayer.LAYER;
    }
}
