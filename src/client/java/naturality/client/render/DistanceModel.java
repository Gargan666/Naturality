package naturality.client.render;

import java.util.function.Predicate;
import naturality.config.NaturalityConfig;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;

public final class DistanceModel extends WrapperBlockStateModel {
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
