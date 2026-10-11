package naturality.mixin;

import naturality.ghast.GhastCrash;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceOrb.class)
public abstract class GhastCrashExperienceMixin {
    @Inject(method="awardWithDirection",at=@At("HEAD"),cancellable=true)
    private static void naturality$holdXP(ServerLevel level,Vec3 pos,Vec3 direction,int amount,CallbackInfo ci) {
        if (GhastCrash.captureExperience(amount)) ci.cancel();
    }
}
