package naturality.mixin;

import naturality.villager.Reputation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBedBlock.class)
public abstract class ReputationBedMixin {
    @Inject(method = "useWithoutItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/AbstractBedBlock;kickVillagerOutOfBed(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"))
    private void naturality$wake(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir) {
        var villagers = level.getEntitiesOfClass(Villager.class, new AABB(pos), Villager::isSleeping);
        if (!villagers.isEmpty()) Reputation.wake(villagers.getFirst(), player);
    }
}
