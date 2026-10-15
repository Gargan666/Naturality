package naturality.client.rendering.breaking.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.renderer.v1.render.submit.ExtendedBlockModelSubmit;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.ModelBakery;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollection;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class BreakingMixins {
    private BreakingMixins() {}

    /** Capture the selected texture before Fabric defers the mesh draw. */
    @Mixin(ExtendedBlockModelSubmit.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class BreakingMeshMixin {
        @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
        private static Function<ChunkSectionLayer, @Nullable RenderType> naturality$texture(
                Function<ChunkSectionLayer, @Nullable RenderType> original,
                @Local(argsOnly = true, ordinal = 1) PoseStack.@Nullable Pose decal) {
            if (decal == null) return original;
            RenderType fallback = original.apply(ChunkSectionLayer.SOLID);
            if (fallback == null) return original;
            int stage = ModelBakery.DESTROY_TYPES.indexOf(fallback);
            boolean oit = stage < 0;
            if (oit) stage = ModelBakery.DESTROY_TYPES_OIT.indexOf(fallback);
            if (stage < 0) return original;
            RenderType texture = naturality.client.rendering.breaking.BreakingEffects.current(stage, oit, fallback);
            return _ -> texture;
        }
    }

    @Mixin(LevelRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class BreakingPositionMixin {
        @WrapMethod(method = "submitBlockDestroyAnimation")
        private void naturality$scope(PoseStack pose, SubmitNodeCollector collector, LevelRenderState state, Operation<Void> original) {
            naturality.client.rendering.breaking.BreakingEffects.at(BlockPos.ZERO, () -> original.call(pose, collector, state));
        }

        @ModifyExpressionValue(method = "submitBlockDestroyAnimation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/state/level/BlockBreakingRenderState;blockPos()Lnet/minecraft/core/BlockPos;"))
        private BlockPos naturality$position(BlockPos pos) {
            naturality.client.rendering.breaking.BreakingEffects.select(pos);
            return pos;
        }
        @WrapOperation(method = "submitBlockEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
        private void naturality$blockEntity(BlockEntityRenderDispatcher dispatcher, BlockEntityRenderState state,
                PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, Operation<Void> original) {
            naturality.client.rendering.breaking.BreakingEffects.at(state.blockPos, () -> original.call(dispatcher, state, pose, collector, camera));
        }
    }

    @Mixin(SubmitNodeCollection.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class BreakingSubmitMixin {
        @WrapOperation(method = {"submitBreakingBlockModel", "submitCrumblingOverlay"},
            at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"))
        private Object naturality$pattern(List<?> list, int stage, Operation<Object> original) {
            Object fallback = original.call(list, stage);
            if ((list == ModelBakery.DESTROY_TYPES || list == ModelBakery.DESTROY_TYPES_OIT)
                    && fallback instanceof RenderType type) {
                return naturality.client.rendering.breaking.BreakingEffects.current(stage, list == ModelBakery.DESTROY_TYPES_OIT, type);
            }
            return fallback;
        }
    }
}
