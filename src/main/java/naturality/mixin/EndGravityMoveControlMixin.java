package naturality.mixin;

import naturality.weather.CeilingPathNavigation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MoveControl.class)
public abstract class EndGravityMoveControlMixin {
    @Shadow @Final protected Mob mob;
    @ModifyVariable(method="tick",at=@At("STORE"),name="yd")
    private double naturality$jumpAwayFromCeiling(double delta) {
        return mob.getNavigation() instanceof CeilingPathNavigation?-delta:delta;
    }
}
