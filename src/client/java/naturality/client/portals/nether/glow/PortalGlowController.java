package naturality.client.portals.nether.glow;

import naturality.client.portals.nether.glow.PortalGlowGeometry.PortalGlowAppearance;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import naturality.Naturality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import naturality.client.portals.nether.glow.PortalGlowGeometry.PortalGlowRenderLayer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import naturality.client.portals.nether.glow.PortalGlowGeometry.PortalMoteGeometry;
import java.util.Optional;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Discovers nearby portals, then maintains exactly one particle per exposed frame edge. */
@org.jspecify.annotations.NullMarked
public final class PortalGlowController {
    private static final int MAX_BLOCKS = 2048;
    private static final int MAX_PARTICLES = 2048;
    private static final int MAX_PORTAL_AREA = 21 * 21;
    private static final SpriteId SPRITE = new SpriteId(naturality.client.api.rendering.AtlasLocations.PARTICLES,
        Naturality.id("particle/nether_portal_glow"));
    private static final Map<BlockPos, Direction.Axis> BLOCKS = new HashMap<>();
    private static final Map<Edge, PortalGlowParticle> PARTICLES = new HashMap<>();
    private static @org.jspecify.annotations.Nullable ClientLevel currentLevel;
    private static @org.jspecify.annotations.Nullable TextureAtlasSprite currentSprite;
    private static PortalGlowAppearance appearance = new PortalGlowAppearance(1);

    private PortalGlowController() { }

    public static float opacity() {
        prepareAppearance(Minecraft.getInstance());
        return appearance.opacity();
    }

    public static void spawnMote(ClientLevel level, BlockPos pos, BlockState state,
                                 double x, double y, double z, double vx, double vy, double vz) {
        Minecraft client = Minecraft.getInstance();
        var cameraEntity = client.getCameraEntity();
        useLevel(level);
        if (cameraEntity == null || client.options.particles().get() == ParticleStatus.MINIMAL
            || pos.distToCenterSqr(cameraEntity.position()) > rangeSquared(client)) return;
        if (client.options.particles().get() == ParticleStatus.DECREASED && client.particleEngine.getRandom().nextBoolean()) return;
        TextureAtlasSprite sprite = prepareAppearance(client);
        client.particleEngine.add(new PortalMoteParticle(level, pos, state.getValue(NetherPortalBlock.AXIS),
            x, y, z, vx, vy, vz, sprite, appearance, client.particleEngine.getRandom()));
    }

    private static TextureAtlasSprite prepareAppearance(Minecraft client) {
        TextureAtlasSprite sprite = client.getAtlasManager().get(SPRITE);
        if (sprite != currentSprite) {
            PARTICLES.values().forEach(PortalGlowParticle::remove);
            PARTICLES.clear();
            currentSprite = sprite;
            appearance = PortalGlowAppearance.load(client);
        }
        return sprite;
    }

    public static void observe(ClientLevel level, BlockPos seed, BlockState state) {
        if (naturality.client.portals.nether.opening.PortalOpeningClient.isOpening(seed)) return;
        if (!naturality.config.live.NaturalityConfig.get().portalChanges.glowEffect) return;
        Minecraft client = Minecraft.getInstance();
        var cameraEntity = client.getCameraEntity();
        useLevel(level);
        if (client.options.particles().get() == ParticleStatus.MINIMAL || BLOCKS.containsKey(seed)
            || BLOCKS.size() >= MAX_BLOCKS || cameraEntity == null) {
            return;
        }
        Direction.Axis axis = state.getValue(NetherPortalBlock.AXIS);
        Direction[] directions = PortalGlowGeometry.edges(axis);
        double rangeSquared = rangeSquared(client);
        Vec3 eye = cameraEntity.position();
        if (seed.distToCenterSqr(eye) > rangeSquared) {
            return;
        }

        // Discover the connected opening on the first display tick, rather than waiting
        // for every individual edge block to receive its own random display tick.
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        pending.add(seed.immutable());
        int found = 0;
        while (!pending.isEmpty() && found < MAX_PORTAL_AREA && BLOCKS.size() < MAX_BLOCKS) {
            BlockPos pos = pending.removeFirst();
            if (!visited.add(pos) || !isPortal(level, pos, axis) || pos.distToCenterSqr(eye) > rangeSquared) {
                continue;
            }
            BLOCKS.put(pos, axis);
            found++;
            for (Direction direction : directions) {
                pending.add(pos.relative(direction));
            }
        }
    }

    public static void tick(Minecraft client) {
        var cameraEntity = client.getCameraEntity();
        useLevel(client.level);
        if (!naturality.config.live.NaturalityConfig.get().portalChanges.glowEffect) {
            PARTICLES.values().forEach(PortalGlowParticle::remove);
            PARTICLES.clear();
            BLOCKS.clear();
            return;
        }
        var currentLevel = PortalGlowController.currentLevel;
        if (currentLevel == null || cameraEntity == null
            || client.options.particles().get() == ParticleStatus.MINIMAL) {
            clear();
            return;
        }
        TextureAtlasSprite sprite = prepareAppearance(client);
        Vec3 eye = cameraEntity.position();
        double rangeSquared = rangeSquared(client);
        BLOCKS.entrySet().removeIf(entry -> entry.getKey().distToCenterSqr(eye) > rangeSquared
            || !isPortal(currentLevel, entry.getKey(), entry.getValue()));
        PARTICLES.entrySet().removeIf(entry -> {
            Edge edge = entry.getKey();
            if (BLOCKS.get(edge.pos()) != edge.axis() || !isFrameEdge(currentLevel, edge)) {
                entry.getValue().remove();
                return true;
            }
            return false;
        });
        for (Map.Entry<BlockPos, Direction.Axis> block : BLOCKS.entrySet()) {
            for (Direction outward : PortalGlowGeometry.edges(block.getValue())) {
                Edge edge = new Edge(block.getKey(), block.getValue(), outward);
                if (!isFrameEdge(currentLevel, edge)) {
                    continue;
                }
                PortalGlowParticle particle = PARTICLES.get(edge);
                if (particle != null && !particle.needsReplacement(currentLevel.getGameTime())) {
                    continue;
                }
                if (particle == null && PARTICLES.size() >= MAX_PARTICLES) {
                    continue;
                }
                if (particle != null) {
                    particle.remove();
                }
                particle = new PortalGlowParticle(currentLevel, edge.pos(), edge.axis(), outward, sprite, appearance);
                PARTICLES.put(edge, particle);
                client.particleEngine.add(particle);
            }
        }
    }

    private static double rangeSquared(Minecraft client) {
        int range = client.options.particles().get() == ParticleStatus.DECREASED ? 16 : 32;
        return range * range;
    }

    private static boolean isPortal(ClientLevel level, BlockPos pos, Direction.Axis axis) {
        if (naturality.client.portals.nether.opening.PortalOpeningClient.isOpening(pos)) return false;
        if (!naturality.api.worlds.LoadedChunks.has(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.NETHER_PORTAL) && state.getValue(NetherPortalBlock.AXIS) == axis;
    }

    private static boolean isFrameEdge(ClientLevel level, Edge edge) {
        BlockPos frame = edge.pos().relative(edge.outward());
        return naturality.api.worlds.LoadedChunks.has(level, frame) && level.getBlockState(frame).is(Blocks.OBSIDIAN);
    }

    private static void useLevel(@org.jspecify.annotations.Nullable ClientLevel level) {
        if (currentLevel != level) {
            clear();
            currentLevel = level;
        }
    }

    private static void clear() {
        PARTICLES.values().forEach(PortalGlowParticle::remove);
        PARTICLES.clear();
        BLOCKS.clear();
        currentSprite = null;
    }

    private record Edge(BlockPos pos, Direction.Axis axis, Direction outward) { }

    /** A stationary edge strip. Back-face rejection affects rendering, never its lifetime. */
    public static final class PortalGlowParticle extends SingleQuadParticle {
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
            if (naturality.client.portals.nether.opening.PortalOpeningClient.hasIntro(portalBlock)) age = 10;
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
            float pulse = naturality.client.portals.nether.opening.PortalOpeningClient.pulse(portalBlock, partialTick);
            int color = ARGB.colorFromFloat(fadeIn * appearance.opacity(), 1 - pulse, 1, 1);
            int light = 0xF000F0;
            // Only trim real corners; adjoining collinear frame tiles stay joined.
            boolean horizontal = inward.getAxis() == Direction.Axis.Y;
            Direction along = horizontal ? (axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH) : Direction.UP;
            double trimStart = isPortal(portalBlock.relative(along.getOpposite())) ? 0 : PortalGlowGeometry.INSET;
            double trimEnd = isPortal(portalBlock.relative(along)) ? 0 : PortalGlowGeometry.INSET;
            if (naturality.client.portals.nether.travel.PortalCrossingClient.clipFrame(portalBlock, axis, inward,
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
            if (!naturality.api.worlds.LoadedChunks.has(level, pos)) return false;
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

    /** A two-sided, velocity-facing square drawn from one actual portal palette color. */
    public static final class PortalMoteParticle extends SingleQuadParticle {
        private static final ParticleLimit LIMIT = new ParticleLimit(512);
        private final BlockPos portal;
        private final Direction.Axis axis;
        private final Vec3 start;
        private final Vec3 target;
        private final Vec3 control;
        private final double initialDistance;
        private Vec3 velocity;
        private double movementTicks;

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
            if (!naturality.config.live.NaturalityConfig.get().portalChanges.portalParticleChanges) { remove(); return; }
            if (!naturality.api.worlds.LoadedChunks.has(level, portal)) {
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
            age++;
            movementTicks += naturality.client.systems.sleep.SleepVisuals.speed();
            double t = Math.min(1, movementTicks / lifetime);
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
            Quaternionf rotation = naturality.client.api.geometry.ParticleGeometry.orientation(velocity, camera.position().subtract(position));
            Vec3 relative = position.subtract(camera.position());
            float u = PortalMoteGeometry.paletteU(proximity);
            float halfV = 0.5F / PortalMoteGeometry.crossWidth(proximity);
            // The shader clips the transverse width; texture X remains the travel axis.
            state.add(getLayer(), (float) relative.x, (float) relative.y, (float) relative.z,
                rotation.x, rotation.y, rotation.z, rotation.w, size,
                u, u, -halfV, halfV, ARGB.colorFromFloat(opacity,
                    1 - naturality.client.portals.nether.opening.PortalOpeningClient.pulse(portal, partialTick), 1, 1), 0xF000F0);
        }

        @Override protected int getLightCoords(float partialTick) { return 0xF000F0; }
        @Override protected Layer getLayer() { return PortalGlowRenderLayer.MOTE_LAYER; }
        @Override public Optional<ParticleLimit> getParticleLimit() { return Optional.of(LIMIT); }
    }
}
