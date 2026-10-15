package naturality.entities.fish.mixins;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import naturality.entities.fish.FishMoveControl;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class FishMixins {
    private FishMixins() {}

    @Mixin(Mob.class)
    @org.jspecify.annotations.NullMarked
    public interface FishMoveControlAccess {
        @Accessor("moveControl") void naturality$control(MoveControl<?> control);
    }

    @Mixin(AbstractFish.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FishMovementMixin {
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
}
