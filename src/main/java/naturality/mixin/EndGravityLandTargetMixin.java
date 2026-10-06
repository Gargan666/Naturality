package naturality.mixin;

import naturality.weather.CeilingPathNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LandRandomPos.class)
public abstract class EndGravityLandTargetMixin {
    @Inject(method="movePosUpOutOfSolid",at=@At("HEAD"),cancellable=true)
    private static void naturality$keepCeilingTarget(PathfinderMob mob,BlockPos pos,CallbackInfoReturnable<BlockPos> cir) {
        if(mob.getNavigation() instanceof CeilingPathNavigation navigation)
            cir.setReturnValue(navigation.isStableDestination(pos)?pos:null);
    }
}
