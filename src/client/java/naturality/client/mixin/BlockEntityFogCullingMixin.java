package naturality.client.mixin;

import naturality.client.fog.FogCulling;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityFogCullingMixin {
    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private void naturality$hiddenBlockEntity(BlockEntity entity, float partialTick,
            ModelFeatureRenderer.CrumblingOverlay overlay, boolean global,
            CallbackInfoReturnable<BlockEntityRenderState> cir) {
        // Global renderers can emit distant beams; their block cell does not bound the effect.
        if (global || !FogCulling.active() || !FogCulling.matches(entity.getLevel())) return;
        var pos = entity.getBlockPos();
        if (FogCulling.hidden(pos.getX() - 2.0, pos.getY() - 2.0, pos.getZ() - 2.0,
                pos.getX() + 3.0, pos.getY() + 3.0, pos.getZ() + 3.0)) cir.setReturnValue(null);
    }
}
