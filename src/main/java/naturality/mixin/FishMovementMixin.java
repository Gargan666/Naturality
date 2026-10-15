package naturality.mixin;
import naturality.fish.FishMoveControl;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractFish.class)
public abstract class FishMovementMixin {
    @Inject(method="<init>",at=@At("TAIL"))
    private void naturality$control(EntityType<?> type,Level level,CallbackInfo ci) {
        ((FishMoveControlAccess)this).naturality$control(new FishMoveControl((AbstractFish)(Object)this));
    }
    @Inject(method="travelInWater",at=@At("HEAD"),cancellable=true)
    private void naturality$travel(Vec3 input,double gravity,boolean falling,double fluid,CallbackInfo ci) {
        AbstractFish fish=(AbstractFish)(Object)this;
        fish.move(MoverType.SELF,fish.getDeltaMovement());
        fish.setDeltaMovement(fish.getDeltaMovement().scale(.9));
        ci.cancel();
    }
}
