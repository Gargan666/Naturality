package naturality.mixin;

import naturality.weather.CeilingPathNavigation;
import naturality.weather.EndGravity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class EndGravityMobNavigationMixin {
    @Shadow protected PathNavigation navigation;
    @Unique private GroundPathNavigation naturality$uprightNavigation;

    @Inject(method="serverAiStep",at=@At("HEAD"))
    private void naturality$changeWalkingSurface(CallbackInfo ci) {
        var mob=(Mob)(Object)this;
        if(EndGravity.inverted(mob)) {
            if(navigation instanceof GroundPathNavigation ground && !(ground instanceof CeilingPathNavigation)) {
                naturality$uprightNavigation=ground;
                ground.stop();
                navigation=new CeilingPathNavigation(mob,mob.level(),ground);
                naturality$clearOldSteering(mob);
            }
        } else if(naturality$uprightNavigation!=null) {
            if(navigation instanceof CeilingPathNavigation) {
                navigation.stop();
                navigation=naturality$uprightNavigation;
                naturality$clearOldSteering(mob);
            }
            naturality$uprightNavigation=null;
        }
    }
    @Unique private void naturality$clearOldSteering(Mob mob) {
        mob.getMoveControl().setWait();
        mob.setJumping(false);
        mob.getBrain().eraseMemory(MemoryModuleType.PATH);
    }
}
