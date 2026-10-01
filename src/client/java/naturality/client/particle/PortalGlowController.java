package naturality.client.particle;

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

/** Discovers nearby portals, then maintains exactly one particle per exposed frame edge. */
public final class PortalGlowController {
    private static final int MAX_BLOCKS = 2048;
    private static final int MAX_PARTICLES = 2048;
    private static final int MAX_PORTAL_AREA = 21 * 21;
    private static final SpriteId SPRITE = new SpriteId(naturality.client.AtlasLocations.PARTICLES,
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
        if (naturality.client.portal.PortalOpeningClient.isOpening(seed)) return;
        if (!naturality.config.NaturalityConfig.get().portalChanges.glowEffect) return;
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
        if (!naturality.config.NaturalityConfig.get().portalChanges.glowEffect) {
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
        if (naturality.client.portal.PortalOpeningClient.isOpening(pos)) return false;
        if (!naturality.util.LoadedChunks.has(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.NETHER_PORTAL) && state.getValue(NetherPortalBlock.AXIS) == axis;
    }

    private static boolean isFrameEdge(ClientLevel level, Edge edge) {
        BlockPos frame = edge.pos().relative(edge.outward());
        return naturality.util.LoadedChunks.has(level, frame) && level.getBlockState(frame).is(Blocks.OBSIDIAN);
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
}
