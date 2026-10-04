package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.breaking.BreakingTextures;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class BreakingPositionMixin {
    @WrapMethod(method = "submitBlockDestroyAnimation")
    private void naturality$scope(PoseStack pose, SubmitNodeCollector collector, LevelRenderState state, Operation<Void> original) {
        BreakingTextures.at(BlockPos.ZERO, () -> original.call(pose, collector, state));
    }

    @ModifyExpressionValue(method = "submitBlockDestroyAnimation", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/state/level/BlockBreakingRenderState;blockPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos naturality$position(BlockPos pos) {
        BreakingTextures.select(pos);
        return pos;
    }
    @WrapOperation(method = "submitBlockEntities", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private void naturality$blockEntity(BlockEntityRenderDispatcher dispatcher, BlockEntityRenderState state,
            PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, Operation<Void> original) {
        BreakingTextures.at(state.blockPos, () -> original.call(dispatcher, state, pose, collector, camera));
    }
}
