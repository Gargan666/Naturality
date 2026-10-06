package naturality.mixin;

import naturality.weather.CeilingPathNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.RandomPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RandomPos.class)
public abstract class EndGravityRandomPosMixin {
    @Inject(method="generateRandomPosTowardDirection",at=@At("RETURN"),cancellable=true)
    private static void naturality$ceilingWanderTarget(PathfinderMob mob,double range,RandomSource random,
            BlockPos direction,CallbackInfoReturnable<BlockPos> cir) {
        if(!(mob.getNavigation() instanceof CeilingPathNavigation))return;
        var surface=CeilingPathNavigation.surfaceAt(mob,cir.getReturnValue(),Math.min(8,Math.abs(direction.getY())+1));
        if(surface!=null)cir.setReturnValue(BlockPos.containing(surface.position(mob).add(0,1e-5,0)));
    }
}
