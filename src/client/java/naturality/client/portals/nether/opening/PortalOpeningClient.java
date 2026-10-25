package naturality.client.portals.nether.opening;

import naturality.portals.nether.opening.PortalOpeningManager;
import naturality.portals.nether.opening.PortalOpeningManager.PortalOpeningPayload;
import naturality.portals.nether.opening.PortalOpeningManager.PortalOpeningTiming;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import naturality.client.portals.nether.glow.PortalGlowGeometry.PortalTextureBrightness;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;

@org.jspecify.annotations.NullMarked
public final class PortalOpeningClient {
    // Chunk compilation reads this concurrently. Entries themselves are immutable.
    private static final Map<BlockPos, Opening> BLOCKS = new ConcurrentHashMap<>();
    private static volatile @org.jspecify.annotations.Nullable ClientLevel currentLevel;
    private record Opening(PortalOpeningPayload data, long start) { }

    public static void initialize() {
        PortalOpeningRenderLayer.initialize();
        PortalOpeningManager.clientOpening = (level, pos) -> level == currentLevel && isOpening(pos);
        if (!naturality.config.startup.FeatureConfig.clientOnly())
            ClientPlayNetworking.registerGlobalReceiver(PortalOpeningPayload.TYPE,
                (payload, context) -> receive(context.client(), payload));
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> { BLOCKS.clear(); currentLevel = null; });
    }

    private static void useLevel(@org.jspecify.annotations.Nullable ClientLevel level) {
        if (level != currentLevel) { BLOCKS.clear(); currentLevel = level; }
    }
    private static void receive(Minecraft client, PortalOpeningPayload data) {
        useLevel(client.level);
        var currentLevel = PortalOpeningClient.currentLevel;
        if (currentLevel == null || !currentLevel.dimension().identifier().toString().equals(data.dimension())) return;
        if (data.elapsed() < 0) {
            BlockPos.betweenClosed(data.min(), data.max()).forEach(BLOCKS::remove);
        } else {
            Opening opening = new Opening(data, currentLevel.getGameTime() - data.elapsed());
            BlockPos.betweenClosed(data.min(), data.max()).forEach(p -> BLOCKS.put(p.immutable(), opening));
        }
        dirty(data);
    }
    private static void dirty(PortalOpeningPayload data) {
        var currentLevel = PortalOpeningClient.currentLevel;
        if (currentLevel == null) return;
        for (int x = data.min().getX() >> 4; x <= data.max().getX() >> 4; x++)
            for (int y = data.min().getY() >> 4; y <= data.max().getY() >> 4; y++)
                for (int z = data.min().getZ() >> 4; z <= data.max().getZ() >> 4; z++)
                    currentLevel.setSectionDirtyWithNeighbors(x, y, z);
    }
    public static boolean hidesTerrain(BlockPos pos, BlockState state) {
        Opening opening = BLOCKS.get(pos);
        return opening != null && (state.is(Blocks.NETHER_PORTAL)
            || state.is(BlockTags.FIRE) && opening.data.fires().contains(pos));
    }
    public static boolean isOpening(BlockPos pos) {
        var level = currentLevel;
        Opening opening = BLOCKS.get(pos);
        return opening != null && level != null
            && !PortalOpeningTiming.ready(level.getGameTime() - opening.start);
    }
    public static float pulse(BlockPos pos, float partialTick) {
        var currentLevel = PortalOpeningClient.currentLevel;
        Opening opening = BLOCKS.get(pos);
        return opening == null || currentLevel == null ? 0
            : PortalOpeningTiming.pulse(currentLevel.getGameTime() - opening.start + partialTick);
    }
    public static boolean hasIntro(BlockPos pos) {
        return BLOCKS.containsKey(pos);
    }
    public static void tick(Minecraft client) {
        useLevel(client.level);
        var currentLevel = PortalOpeningClient.currentLevel;
        if (currentLevel == null) return;
        // Keep rendering completed portals until out of view, avoiding an async chunk-mesh handoff flash.
        for (Opening opening : new HashSet<>(BLOCKS.values())) {
            var player = client.player;
            boolean far = player != null && opening.data.min().distToCenterSqr(player.position()) > 128 * 128;
            boolean unloaded = !naturality.api.worlds.LoadedChunks.has(currentLevel, opening.data.min()) || !naturality.api.worlds.LoadedChunks.has(currentLevel, opening.data.max());
            boolean expired = currentLevel.getGameTime() - opening.start >= PortalOpeningTiming.END_TICK;
            boolean disabled = expired && !naturality.config.live.NaturalityConfig.get().portalChanges.portalBlockChanges;
            boolean gone = expired && java.util.stream.StreamSupport.stream(
                BlockPos.betweenClosed(opening.data.min(), opening.data.max()).spliterator(), false)
                .noneMatch(p -> currentLevel.getBlockState(p).is(Blocks.NETHER_PORTAL));
            if (unloaded || far || gone || disabled) {
                BLOCKS.entrySet().removeIf(e -> e.getValue() == opening);
                dirty(opening.data);
            } else if (currentLevel.getGameTime() - opening.start >= PortalOpeningTiming.READY_TICK) {
                BlockState block = currentLevel.getBlockState(opening.data.min());
                if (block.is(Blocks.NETHER_PORTAL))
                    naturality.client.portals.nether.glow.PortalGlowController.observe(currentLevel, opening.data.min(), block);
            }
        }
    }
    public static void extract(ParticlesRenderState output, Frustum frustum, Camera camera, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        var currentLevel = PortalOpeningClient.currentLevel;
        if (currentLevel == null || client.level != currentLevel || BLOCKS.isEmpty()) return;
        PortalOpeningRenderState state = new PortalOpeningRenderState();
        List<BlockStateModelPart> parts = new ArrayList<>();
        RandomSource random = RandomSource.create(0);
        for (var entry : BLOCKS.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!naturality.api.worlds.LoadedChunks.has(currentLevel, pos) || !frustum.isVisible(new AABB(pos).inflate(0.5))) continue;
            BlockState block = currentLevel.getBlockState(pos);
            Opening opening = entry.getValue();
            float age = currentLevel.getGameTime() - opening.start + partialTick;
            boolean fire = block.is(BlockTags.FIRE) && opening.data.fires().contains(pos);
            if (!fire && !block.is(Blocks.NETHER_PORTAL)) continue;
            float progress = fire ? PortalOpeningTiming.fireAlpha(age) : PortalOpeningTiming.reveal(age);
            if (progress <= 0) continue;
            int color = fire ? ARGB.colorFromFloat(progress, 1, 1, 1)
                : ARGB.colorFromFloat(1, progress, PortalOpeningTiming.pulse(age), 1);
            var layer = fire ? PortalOpeningRenderLayer.FIRE : progress >= 1
                ? PortalOpeningRenderLayer.SOLID_PORTAL : PortalOpeningRenderLayer.PORTAL;
            var model = client.getModelManager().getBlockStateModelSet().get(block);
            if (fire) {
                state.addFire(model, currentLevel, pos, block,
                    Vec3.atLowerCornerOf(pos).subtract(camera.position()), progress);
                continue;
            }
            random.setSeed(block.getSeed(pos));
            parts.clear();
            model.collectParts(random, parts);
            Vec3 offset = Vec3.atLowerCornerOf(pos).subtract(camera.position());
            for (var part : parts) {
                for (var quad : part.getQuads(null)) state.addModelQuad(layer, quad, offset, color);
                for (Direction face : Direction.values()) {
                    if (currentLevel.getBlockState(pos.relative(face)).isSolidRender()) continue;
                    for (var quad : part.getQuads(face)) state.addModelQuad(layer, quad, offset, color);
                }
            }
        }
        if (!state.isEmpty()) output.add(state);
    }

    /** Reuses actual baked fire/portal geometry and animated atlas UVs, rather than approximating a flame sprite. */
    public static final class PortalOpeningRenderState extends QuadParticleRenderState {
        private record Draw(BakedQuad quad, Vec3 offset, int color) { }
        private final Map<SingleQuadParticle.Layer, List<Draw>> draws = new HashMap<>();
        private record FireDraw(float[] positions, float[] uv, int color) { }
        private final List<FireDraw> fires = new ArrayList<>();
        public void addFire(net.minecraft.client.renderer.block.dispatch.BlockStateModel model,
                net.minecraft.client.renderer.block.BlockAndTintGetter level, net.minecraft.core.BlockPos pos,
                net.minecraft.world.level.block.state.BlockState block, Vec3 offset, float alpha) {
            var emitter = net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().quadEmitter(q -> {
                float[] positions = new float[12], uv = new float[8];
                for (int i = 0; i < 4; i++) {
                    positions[i * 3] = (float) (q.x(i) + offset.x);
                    positions[i * 3 + 1] = (float) (q.y(i) + offset.y);
                    positions[i * 3 + 2] = (float) (q.z(i) + offset.z);
                    uv[i * 2] = q.u(i); uv[i * 2 + 1] = q.v(i);
                }
                fires.add(new FireDraw(positions, uv, (Math.round(alpha * 255) << 24)
                    | (naturality.client.blocks.fire.ProceduralFire.seedColor(pos) & 0xFFFFFF)));
            });
            model.emitQuads(emitter, level, pos, block, net.minecraft.util.RandomSource.create(block.getSeed(pos)), _ -> false);
        }
        private final int brightnessRange = PortalTextureBrightness.packedRange(net.minecraft.client.Minecraft.getInstance());
        public void addModelQuad(SingleQuadParticle.Layer layer, BakedQuad quad, Vec3 offset, int color) {
            draws.computeIfAbsent(layer, _ -> new ArrayList<>()).add(new Draw(quad, offset, color));
        }
        @Override public Set<SingleQuadParticle.Layer> layers() {
            var layers = new HashSet<>(draws.keySet());
            if (!fires.isEmpty()) layers.add(PortalOpeningRenderLayer.FIRE);
            return layers;
        }
        @Override public boolean isEmpty() { return draws.isEmpty() && fires.isEmpty(); }
        @Override public void clear() { draws.clear(); fires.clear(); }
        @Override public void submit(SubmitNodeCollector collector, CameraRenderState camera) {
            if (!isEmpty()) collector.submitQuadParticleGroup(this);
        }
        @Override public void buildLayer(SingleQuadParticle.Layer layer, VertexConsumer buffer) {
            if (layer == PortalOpeningRenderLayer.FIRE) for (var fire : fires) for (int i = 0; i < 4; i++) {
                buffer.addVertex(fire.positions[i * 3], fire.positions[i * 3 + 1], fire.positions[i * 3 + 2])
                    .setUv(fire.uv[i * 2], fire.uv[i * 2 + 1]).setColor(fire.color).setLight(0xF000F0);
            }
            for (Draw draw : draws.getOrDefault(layer, List.of())) {
                for (int i = 0; i < 4; i++) {
                    var p = draw.quad.position(i);
                    long uv = draw.quad.packedUV(i);
                    buffer.addVertex((float) (p.x() + draw.offset.x), (float) (p.y() + draw.offset.y),
                        (float) (p.z() + draw.offset.z)).setUv(UVPair.unpackU(uv), UVPair.unpackV(uv))
                        .setColor(draw.color).setLight(brightnessRange);
                }
            }
        }
    }
}
