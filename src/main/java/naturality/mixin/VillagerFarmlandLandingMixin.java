package naturality.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep fall damage, but never let a villager's landing destroy a planted field. */
@Mixin(FarmlandBlock.class)
public abstract class VillagerFarmlandLandingMixin {
    @Inject(method = "fallOn", at = @At("HEAD"), cancellable = true)
    private void naturality$protectFromVillager(Level level, BlockState state, BlockPos pos, Entity entity,
            double fallDistance, CallbackInfo ci) {
        if (entity instanceof Villager) {
            var farmland = (FarmlandBlock)(Object)this;
            entity.causeFallDamage(fallDistance * (1.0F - farmland.getFallDistanceReduction()),
                1.0F, entity.damageSources().fall());
            ci.cancel();
        }
    }
}
