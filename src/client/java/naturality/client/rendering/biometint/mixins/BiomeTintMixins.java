package naturality.client.rendering.biometint.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.QuadInstance;
import naturality.client.rendering.biometint.BiomeTint;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native color-source identification and terrain-only application. */
public final class BiomeTintMixins {
    private BiomeTintMixins() {}

    @Mixin(BlockTintSources.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class BiomeTintSourceMixin {
        @Inject(method = {"grass", "grassBlock", "doubleTallGrass", "sugarCane", "foliage", "dryFoliage", "water"},
            at = @At("RETURN"), cancellable = true)
        private static void naturality$identifyBiomeSource(CallbackInfoReturnable<BlockTintSource> cir) {
            cir.setReturnValue(new BiomeTint.Source(cir.getReturnValue()));
        }
    }

    @Mixin(ModelBlockRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class BiomeTintModelMixin {
        @SuppressWarnings("null") @Shadow @Final private BlockColors blockColors;
        @Unique private BiomeTint.@Nullable Samples naturality$tintSamples;

        @WrapMethod(method = "tesselateBlock")
        private void naturality$scopeTintSamples(BlockQuadOutput output, float x, float y, float z,
                BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model,
                long seed, Operation<Void> original) {
            var previous = naturality$tintSamples;
            naturality$tintSamples = null;
            try {
                original.call(output, x, y, z, level, pos, state, model, seed);
            } finally {
                naturality$tintSamples = previous;
            }
        }

        @WrapOperation(method = "putQuadWithTint", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/QuadInstance;multiplyColor(I)V"))
        private void naturality$worldTint(QuadInstance instance, int color, Operation<Void> original,
                BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level,
                BlockState state, BlockPos pos, BakedQuad quad) {
            var source = BiomeTint.source(blockColors, state, quad.materialInfo().tintIndex());
            if (source == null) { original.call(instance, color); return; }
            var samples = naturality$tintSamples;
            if (samples == null || !samples.matches(source, level, state, pos))
                naturality$tintSamples = samples = new BiomeTint.Samples(source, level, state, pos);
            var offset = state.getOffset(pos);
            for (int i = 0; i < 4; i++) {
                var p = quad.position(i);
                int tint = samples.at(p.x() + offset.x, p.y() + offset.y, p.z() + offset.z);
                instance.setColor(i, ARGB.multiply(instance.getColor(i), tint));
            }
        }
    }

    @Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl", remap = false)
    @org.jspecify.annotations.NullMarked
    public static abstract class BiomeTintIndigoMixin {
        @SuppressWarnings("null") @Shadow @Final private BlockColors blockColors;
        @SuppressWarnings("null") @Shadow private BlockAndTintGetter level;
        @SuppressWarnings("null") @Shadow private BlockPos pos;
        @SuppressWarnings("null") @Shadow private BlockState blockState;
        @Unique private BiomeTint.@Nullable Samples naturality$tintSamples;

        @WrapMethod(method = "tesselateBlock")
        private void naturality$scopeTintSamples(QuadEmitter output, float x, float y, float z,
                BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model,
                long seed, Operation<Void> original) {
            var previous = naturality$tintSamples;
            naturality$tintSamples = null;
            try {
                original.call(output, x, y, z, level, pos, state, model, seed);
            } finally {
                naturality$tintSamples = previous;
            }
        }

        @Inject(method = "tintQuad", at = @At("HEAD"), cancellable = true)
        private void naturality$worldTint(MutableQuadView quad, CallbackInfo ci) {
            var source = BiomeTint.source(blockColors, blockState, quad.tintIndex());
            if (source == null) return;
            var samples = naturality$tintSamples;
            if (samples == null || !samples.matches(source, level, blockState, pos))
                naturality$tintSamples = samples = new BiomeTint.Samples(source, level, blockState, pos);
            var offset = blockState.getOffset(pos);
            for (int i = 0; i < 4; i++) {
                int tint = samples.at(
                    quad.x(i) + offset.x, quad.y(i) + offset.y, quad.z(i) + offset.z);
                quad.color(i, ARGB.multiply(quad.color(i), tint));
            }
            ci.cancel();
        }
    }
}
