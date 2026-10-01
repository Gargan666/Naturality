package naturality.client.mixin;

import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class PortalStateEmissiveMixin {
    @Inject(method = "emissiveRendering", at = @At("HEAD"), cancellable = true)
    private void naturality$portalIsEmissive(CallbackInfoReturnable<Boolean> ci) {
        if (naturality.config.NaturalityConfig.get().portalChanges.portalBlockChanges && ((BlockBehaviour.BlockStateBase) (Object) this).getBlock() instanceof NetherPortalBlock) {
            ci.setReturnValue(true);
        }
    }
}
