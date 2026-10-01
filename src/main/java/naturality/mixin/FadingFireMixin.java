package naturality.mixin;
import naturality.portal.PortalOpeningManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public abstract class FadingFireMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void naturality$holdFlame(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (PortalOpeningManager.fadingFire(level, pos)) {
            level.scheduleTick(pos, state.getBlock(), 10);
            ci.cancel();
        }
    }
}
