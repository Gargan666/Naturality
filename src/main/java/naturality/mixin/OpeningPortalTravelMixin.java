package naturality.mixin;
import naturality.portal.PortalOpeningManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetherPortalBlock.class)
public abstract class OpeningPortalTravelMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void naturality$waitForOpening(BlockState state, Level level, BlockPos pos, Entity entity,
            InsideBlockEffectApplier effects, boolean precise, CallbackInfo ci) {
        if (PortalOpeningManager.isOpening(level, pos)) { ci.cancel(); return; }
        naturality.portal.PortalCrossing.enter(entity, pos, state.getValue(NetherPortalBlock.AXIS));
    }
}
