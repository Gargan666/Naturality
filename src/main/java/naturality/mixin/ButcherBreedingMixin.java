package naturality.mixin;

import naturality.villager.ButcherWork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Animal.class)
public abstract class ButcherBreedingMixin {
    @Inject(method = "finalizeSpawnChildFromBreeding", at = @At("TAIL"))
    private void naturality$born(ServerLevel level, Animal partner, AgeableMob child, CallbackInfo ci) {
        if (child != null) ButcherWork.born((Animal)(Object)this, partner, level.getGameTime());
    }
}
