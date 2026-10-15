package naturality.systems.fishing.mixins;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import naturality.systems.fishing.VillagerBobbers.PlayerFishingReturn;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import naturality.systems.fishing.VillagerBobbers.FishingSpots;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class FishingMixins {
    private FishingMixins() {}

    @Mixin(FishingHook.class)
    @org.jspecify.annotations.NullMarked
    public interface FishingHookLootAccess {
        @Invoker("calculateOpenWater") boolean naturality$calculateOpenWater(BlockPos pos);
        @Accessor("openWater") void naturality$setOpenWater(boolean open);
    }

    @Mixin(FishingHook.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FishingHookReturnMixin {
        @Redirect(method = "retrieve", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
        private boolean naturality$returnCatchOnHook(Level level, Entity entity) {
            FishingHook hook = (FishingHook)(Object)this;
            if (entity instanceof ItemEntity item && level instanceof ServerLevel server && hook.getPlayerOwner() != null) {
                return level.addFreshEntity(new PlayerFishingReturn(server, hook.getPlayerOwner(), hook.position(), item.getItem()));
            }
            return level.addFreshEntity(entity);
        }
    }

    @Mixin(ServerLevel.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FishingSpotUpdatesMixin {
        @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
        private void naturality$invalidateFishing(BlockPos pos, BlockState oldState, BlockState state,
                int flags, CallbackInfo ci) {
            if (oldState != state) FishingSpots.blockChanged((ServerLevel)(Object)this, pos, oldState, state);
        }
    }
}
