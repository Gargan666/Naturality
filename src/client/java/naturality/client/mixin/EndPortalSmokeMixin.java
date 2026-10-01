package naturality.client.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndPortalBlock.class)
public class EndPortalSmokeMixin {
    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void naturality$noSmoke(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (naturality.config.NaturalityConfig.get().portalChanges.suppressEndSmoke) ci.cancel();
    }
}
