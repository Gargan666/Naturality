package naturality.mixin;

import naturality.villager.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class ReputationChestBreakMixin {
    @SuppressWarnings("null") @Shadow protected ServerLevel level;
    @SuppressWarnings("null") @Shadow @Final protected ServerPlayer player;
    @Unique private boolean naturality$breakingVillageLoot;
    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void naturality$before(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        var entity = level.getBlockEntity(pos);
        naturality$breakingVillageLoot = !player.isCreative() && entity instanceof VillageChest chest
            && chest.naturality$isVillageChest() && entity instanceof Container contents && !contents.isEmpty();
    }
    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void naturality$after(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (naturality$breakingVillageLoot && cir.getReturnValueZ()) Reputation.change(player, -3);
        naturality$breakingVillageLoot = false;
    }
}
