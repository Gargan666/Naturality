package naturality.api.blocks.mixins;

import naturality.api.blocks.FallingLayerBlock;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native falling entities already clear WATERLOGGED on launch; layer placement resolves after movement. */
public final class FallingLayersMixin {
    private FallingLayersMixin() {}

    @Mixin(FallingBlockEntity.class)
    public static abstract class FallingLayerLandingMixin {
        @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;applyEffectsFromBlocks()V"), cancellable = true)
        private void naturality$settleLayers(CallbackInfo ci) {
            if (FallingLayerBlock.settle((FallingBlockEntity)(Object)this)) ci.cancel();
        }
    }
}
