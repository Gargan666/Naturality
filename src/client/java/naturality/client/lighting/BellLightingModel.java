package naturality.client.lighting;

import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Bell supports have stretched UVs; their lighting cells follow texture texels. */
public final class BellLightingModel extends WrapperBlockStateModel {
    // Reserved opaque-quad marker, outside foliage, snow and underwater tags.
    public static final int TEXEL_LIGHTING_TAG = 242;

    private BellLightingModel(BlockStateModel wrapped) { super(wrapped); }
    private record GeometryKey(Object wrappedKey) {}

    @Override public @org.jspecify.annotations.Nullable Object createGeometryKey(BlockAndTintGetter level,
            BlockPos pos, BlockState state, RandomSource random) {
        var key = wrapped.createGeometryKey(level, pos, state, random);
        // Cached tagged quads must not alias the original model's unmarked quads.
        return key == null ? null : new GeometryKey(key);
    }

    public static void initialize() {
        ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register(
            ModelModifier.WRAP_LAST_PHASE, (model, bake) -> bake.state().is(Blocks.BELL)
                ? new BellLightingModel(model) : model));
    }

    @Override public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos,
            BlockState state, RandomSource random, Predicate<@org.jspecify.annotations.Nullable Direction> cull) {
        emitter.pushTransform(quad -> {
            for (int i = 0; i < 4; i++) {
                int color = quad.color(i);
                if ((color >>> 24) == 255)
                    quad.color(i, (color & 0xFFFFFF) | (TEXEL_LIGHTING_TAG << 24));
            }
            return true;
        });
        try { wrapped.emitQuads(emitter, level, pos, state, random, cull); }
        finally { emitter.popTransform(); }
    }
}
