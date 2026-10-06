package naturality.mixin;

import naturality.villager.FishingSpots;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class FishingSpotUpdatesMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void naturality$invalidateFishing(BlockPos pos, BlockState oldState, BlockState state,
            int flags, CallbackInfo ci) {
        if (oldState != state) FishingSpots.blockChanged((ServerLevel)(Object)this, pos, oldState, state);
    }
}
