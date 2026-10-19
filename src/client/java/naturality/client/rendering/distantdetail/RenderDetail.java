package naturality.client.rendering.distantdetail;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import naturality.config.live.NaturalityConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;

/** Section-sized LODs with hysteresis and bounded remesh requests. No world scans. */
public final class RenderDetail {
    private static final Map<Long, Detail> SECTIONS = new ConcurrentHashMap<>();
    private static volatile @Nullable ClientLevel world;
    private static volatile Vec3 camera = Vec3.ZERO;
    private static volatile long epoch;
    private static final ThreadLocal<Cursor> CURSOR = ThreadLocal.withInitial(Cursor::new);
    private static final class Cursor {
        long key, epoch = -1; Detail detail; int builds;
        final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap fixed = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    }
    public static void beginBuild() { CURSOR.get().builds++; }
    public static void endBuild() { var cursor = CURSOR.get(); if (--cursor.builds == 0) cursor.fixed.clear(); }
    private static int remember(Cursor cursor, long key, int tier) {
        if (cursor.builds > 0) cursor.fixed.put(key, tier);
        return tier;
    }
    private static int tick;
    private static int settings;
    private static @Nullable Vec3 lastScan;
    private static boolean pendingTransitions;
    public static boolean settled() { return !pendingTransitions; }
    private static final class Detail { volatile int tier; boolean pending; Detail(int tier) { this.tier = tier; } }
    private RenderDetail() {}
    public static void initialize() { ClientTickEvents.END_CLIENT_TICK.register(RenderDetail::tick); }
    public static void view(@Nullable ClientLevel level, Vec3 position) {
        if (world != level) {
            SECTIONS.clear(); world = level; epoch++;
            lastScan = null; pendingTransitions = false;
            var effects = NaturalityConfig.get().effects;
            settings = (effects.distantLeaves ? 1 : 0) | (effects.distantTextures ? 2 : 0) | (effects.distantDetails ? 8 : 0);
        }
        camera = position;
    }
    public static int tier(double distance, int previous) {
        var effects = NaturalityConfig.get().effects;
        if (effects.distantDetails && distance >= (previous >= 2 ? 160 : 192)) return 2;
        if (effects.distantLeaves && distance >= (previous >= 1 ? 128 : 160)) return 1;
        return 0;
    }
    public static boolean reducedDetails(BlockPos pos) {
        return NaturalityConfig.get().effects.distantDetails && at(pos) >= 2;
    }
    private static int sectionTier(long section, int previous) {
        return tier(distance(section), previous);
    }
    private static double distance(long section) {
        Vec3 p = camera;
        double x = SectionPos.x(section) * 16.0, y = SectionPos.y(section) * 16.0, z = SectionPos.z(section) * 16.0;
        double dx = Math.max(Math.max(x - p.x, p.x - x - 16), 0);
        double dy = Math.max(Math.max(y - p.y, p.y - y - 16), 0);
        double dz = Math.max(Math.max(z - p.z, p.z - z - 16), 0);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    public static int at(BlockPos pos) {
        if (world == null) return 0;
        long key = SectionPos.asLong(pos);
        var cursor = CURSOR.get();
        long currentEpoch = epoch;
        if (cursor.builds > 0) {
            int fixed = cursor.fixed.getOrDefault(key, -1);
            if (fixed >= 0) return fixed;
        }
        if (cursor.epoch == currentEpoch && cursor.key == key) return remember(cursor, key, cursor.detail.tier);
        var existing = SECTIONS.get(key);
        // Unknown overflow falls back to full detail, never an untracked coarse mesh.
        if (existing == null) {
            if (SECTIONS.size() >= 32768) return 0;
            existing = SECTIONS.computeIfAbsent(key, k -> new Detail(sectionTier(k, 0)));
        }
        cursor.key = key; cursor.epoch = currentEpoch; cursor.detail = existing;
        return remember(cursor, key, existing.tier);
    }
    private static void tick(Minecraft client) {
        view(client.level, client.gameRenderer.mainCamera().position());
        var level = world;
        if (level == null || ++tick % 4 != 0) return;
        var effects = NaturalityConfig.get().effects;
        int flags = (effects.distantLeaves ? 1 : 0) | (effects.distantTextures ? 2 : 0) | (effects.distantDetails ? 8 : 0);
        if (!pendingTransitions && flags == settings && lastScan != null
                && camera.distanceToSqr(lastScan) < 1 && tick % 100 != 0) return;
        lastScan = camera;
        pendingTransitions = false;
        if (settings != flags) {
            settings = flags;
            SECTIONS.values().forEach(detail -> detail.pending = true);
        }
        int coarseBudget = 8, nearBudget = 32;
        boolean pruned = false;
        for (var entry : SECTIONS.entrySet()) {
            long key = entry.getKey();
            int x = SectionPos.x(key), y = SectionPos.y(key), z = SectionPos.z(key);
            if (!level.hasChunk(x, z)) { SECTIONS.remove(key, entry.getValue()); pruned = true; continue; }
            var detail = entry.getValue();
            int next = sectionTier(key, detail.tier);
            if (next == detail.tier && !detail.pending) continue;
            boolean towardCamera = next < detail.tier;
            if (towardCamera ? nearBudget <= 0 : coarseBudget <= 0) { pendingTransitions = true; continue; }
            if (towardCamera) nearBudget--; else coarseBudget--;
            detail.tier = next;
            detail.pending = false;
            // Neighbors retain the same-tier rule at every LOD boundary.
            // Only face-sharing sections depend on our leaf boundaries. Avoid
            // rebuilding the 20 unrelated diagonals in a 3x3x3 neighborhood.
            level.setSectionRangeDirty(x - 1, y, z, x + 1, y, z);
            level.setSectionRangeDirty(x, y - 1, z, x, y - 1, z);
            level.setSectionRangeDirty(x, y + 1, z, x, y + 1, z);
            level.setSectionRangeDirty(x, y, z - 1, x, y, z - 1);
            level.setSectionRangeDirty(x, y, z + 1, x, y, z + 1);
        }
        if (pruned) epoch++;
    }

    public static final class DistanceModel extends WrapperBlockStateModel {
        private record Key(Object model, int tier) {}
        private static final Object HIDDEN_PLANT = new Object();
        private final boolean smallPlant, upperPlant;
        public DistanceModel(BlockStateModel model, BlockState state) {
            super(model);
            var block = state.getBlock();
            smallPlant = block instanceof TallGrassBlock || block instanceof FlowerBlock || block instanceof TallFlowerBlock
                || block instanceof FlowerBedBlock || block instanceof CactusFlowerBlock
                || block instanceof ShortDryGrassBlock || block instanceof TallDryGrassBlock || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN);
            upperPlant = smallPlant && state.hasProperty(DoublePlantBlock.HALF) && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
        }
        boolean hiddenPlant(BlockPos pos) {
            // Both halves share the lower section's selection, including at Y=16 boundaries.
            return smallPlant && RenderDetail.reducedDetails(upperPlant ? pos.below() : pos);
        }
        public static void initialize() {
            ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register(
                ModelModifier.WRAP_LAST_PHASE, (model, bake) -> bake.state().is(net.minecraft.tags.BlockTags.FIRE)
                    ? model : new DistanceModel(model, bake.state())));
        }
        @Override public @org.jspecify.annotations.Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos,
                BlockState state, RandomSource random) {
            if (hiddenPlant(pos)) return HIDDEN_PLANT;
            int tier = RenderDetail.at(pos);
            if (tier > 0 && state.getBlock() instanceof LeavesBlock) return null;
            var key = wrapped.createGeometryKey(level, pos, state, random);
            int variant = tier | (NaturalityConfig.get().effects.distantTextures ? 4 : 0);
            return key == null || variant == 0 ? key : new Key(key, variant);
        }
        private static boolean leaf(BlockAndTintGetter level, BlockPos pos, BlockState state) {
            return state.getBlock() instanceof LeavesBlock && state.getFluidState().isEmpty()
                && !level.getBlockState(pos.above()).is(Blocks.SNOW);
        }
        public static boolean fullFace(QuadView quad) {
            var face = quad.cullFace();
            if (face == null) return false;
            int axis = face.getAxis().ordinal();
            float boundary = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
            int corners = 0;
            for (int i = 0; i < 4; i++) {
                if (Math.abs(quad.posByIndex(i, axis) - boundary) > .0001) return false;
                float a = quad.posByIndex(i, (axis + 1) % 3), b = quad.posByIndex(i, (axis + 2) % 3);
                if (Math.abs(a - Math.round(a)) > .0001 || Math.abs(b - Math.round(b)) > .0001 || a < 0 || a > 1 || b < 0 || b > 1) return false;
                corners |= 1 << (Math.round(a) + 2 * Math.round(b));
            }
            return corners == 15;
        }
        @Override public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                RandomSource random, Predicate<@org.jspecify.annotations.Nullable Direction> cull) {
            if (hiddenPlant(pos)) return;
            int tier = RenderDetail.at(pos);
            if (!state.getFluidState().isEmpty()) { wrapped.emitQuads(emitter, level, pos, state, random, cull); return; }
            boolean solidLeaf = tier > 0 && NaturalityConfig.get().effects.distantLeaves && leaf(level, pos, state);
            // The shader fades this pair per fragment; camera travel never remeshes
            // a section merely to change its texture. Original UV detail is retained.
            boolean average = NaturalityConfig.get().effects.distantTextures;
            if (!solidLeaf && !average) { wrapped.emitQuads(emitter, level, pos, state, random, cull); return; }
            emitter.pushTransform(quad -> {
                if (quad.chunkLayer() == ChunkSectionLayer.TRANSLUCENT || quad.animated()) return true;
                float u = 0, v = 0;
                for (int i = 0; i < 4; i++) { u += quad.u(i); v += quad.v(i); }
                var source = DistantTextures.find(u / 4, v / 4);
                if (source == null) return true;
                // Glass needs its original sprite/mask and transparent interior,
                // including custom Fabric quads that still arrive as cutout.
                if (naturality.client.blocks.glass.GlassShine.isGlass(source.contents().name())) return true;
                boolean cubeLeaf = solidLeaf && fullFace(quad);
                if (!average && !cubeLeaf) return true;
                var variant = DistantTextures.variant(source, cubeLeaf, average);
                var sprite = variant.sprite();
                if (sprite == source) return true;
                if (cubeLeaf && variant.opaque()) quad.chunkLayer(ChunkSectionLayer.SOLID);
                // Never remove a boundary face against a detailed or snow-covered neighbor.
                var face = quad.cullFace();
                if (cubeLeaf && variant.opaque() && face != null) {
                    var neighbor = pos.relative(face);
                    var adjacent = level.getBlockState(neighbor);
                    if (adjacent.is(state.getBlock()) && leaf(level, neighbor, adjacent)
                            && RenderDetail.at(neighbor) == tier) return false;
                }
                for (int i = 0; i < 4; i++) {
                    float x = (quad.u(i) - source.getU0()) / (source.getU1() - source.getU0());
                    float y = (quad.v(i) - source.getV0()) / (source.getV1() - source.getV0());
                    quad.uv(i, sprite.getU0() + x * (sprite.getU1() - sprite.getU0()), sprite.getV0() + y * (sprite.getV1() - sprite.getV0()));
                }
                return true;
            });
            try {
                wrapped.emitQuads(emitter, level, pos, state, random, cull);
            }
            finally { emitter.popTransform(); }
        }
    }
}
