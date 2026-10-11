package naturality.mixin;

import naturality.sleep.SleepAcceleration;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BedRule.class)
public class SleepBedRuleMixin {
    @Inject(method = "canSleep", at = @At("HEAD"), cancellable = true)
    private void naturality$anyTime(Level level, CallbackInfoReturnable<Boolean> cir) {
        if (SleepAcceleration.enabled() && ((BedRule)(Object)this).canSleep() == BedRule.Rule.WHEN_DARK) cir.setReturnValue(true);
    }
}
