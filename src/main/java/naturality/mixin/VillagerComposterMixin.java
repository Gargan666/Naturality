package naturality.mixin;

import naturality.villager.ProfessionWork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.WorkAtComposter;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorkAtComposter.class)
public abstract class VillagerComposterMixin {
    @Redirect(method = "compostItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/ComposterBlock;extractProduce(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState naturality$collect(Entity entity, BlockState state, Level level, BlockPos pos) {
        var empty = state.setValue(ComposterBlock.LEVEL, 0);
        level.setBlockAndUpdate(pos, empty);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(entity, empty));
        level.playSound(null, pos, SoundEvents.COMPOSTER_EMPTY, SoundSource.BLOCKS, 1, 1);
        ProfessionWork.store((ServerLevel)level, (Villager)entity, new ItemStack(Items.BONE_MEAL));
        return empty;
    }
}
