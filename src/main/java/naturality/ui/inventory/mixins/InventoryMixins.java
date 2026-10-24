package naturality.ui.inventory.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.ui.inventory.TrashSlot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@NullMarked
public final class InventoryMixins {
    private InventoryMixins() {}

    @Mixin(AbstractContainerMenu.class)
    public static abstract class Menu implements TrashSlot.MenuAccess {
        @Shadow protected abstract Slot addSlot(Slot slot);
        @Shadow protected abstract void resetQuickCraft();

        @Unique
        @Override
        public void naturality$ensureTrashSlot() {
            TrashSlot trash = TrashSlot.create((AbstractContainerMenu) (Object) this);
            if (trash != null) addSlot(trash);
        }

        @Inject(method = {"addSlotListener", "setSynchronizer", "initializeContents"}, at = @At("HEAD"))
        private void naturality$attachBeforeSynchronization(CallbackInfo ci) {
            naturality$ensureTrashSlot();
        }

        @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
        private void naturality$trashClick(int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
            if (TrashSlot.click((AbstractContainerMenu) (Object) this, slot, button, input, player)) {
                resetQuickCraft();
                ci.cancel();
            }
        }

        @Inject(method = "canDragTo", at = @At("HEAD"), cancellable = true)
        private void naturality$excludeTrashFromDrag(Slot slot, CallbackInfoReturnable<Boolean> cir) {
            if (slot instanceof TrashSlot) cir.setReturnValue(false);
        }

        @Inject(method = "canTakeItemForPickAll", at = @At("HEAD"), cancellable = true)
        private void naturality$keepTrashOutOfCollection(ItemStack carried, Slot target, CallbackInfoReturnable<Boolean> cir) {
            if (target instanceof TrashSlot) cir.setReturnValue(false);
        }

        @WrapMethod(method = "moveItemStackTo")
        private boolean naturality$excludeTrashFromTransfers(ItemStack stack, int start, int end, boolean backwards,
                Operation<Boolean> original) {
            // Vanilla's merge pass ignores mayPlace; trim the appended slot from every bulk range.
            TrashSlot trash = TrashSlot.find((AbstractContainerMenu) (Object) this);
            int limit = trash == null ? end : Math.min(end, trash.index);
            return start < limit && original.call(stack, start, limit, backwards);
        }
    }

    @Mixin(InventoryMenu.class)
    public static abstract class PlayerInventory {
        @Inject(method = "<init>", at = @At("TAIL"))
        private void naturality$createPlayerStorage(CallbackInfo ci) {
            TrashSlot.attach((AbstractContainerMenu) (Object) this);
        }
    }

    @Mixin(Player.class)
    public static abstract class Persistence {
        @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
        private void naturality$save(ValueOutput output, CallbackInfo ci) {
            TrashSlot.save((Player) (Object) this, output);
        }

        @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
        private void naturality$load(ValueInput input, CallbackInfo ci) {
            TrashSlot.load((Player) (Object) this, input);
        }

        @Inject(method = "dropEquipment", at = @At("TAIL"))
        private void naturality$dropTrash(ServerLevel level, CallbackInfo ci) {
            TrashSlot.dropOnDeath((Player) (Object) this, level);
        }
    }

    @Mixin(ServerPlayer.class)
    public static abstract class Respawn {
        @Inject(method = "transferInventoryXpAndScore", at = @At("TAIL"))
        private void naturality$transferTrash(Player previous, CallbackInfo ci) {
            TrashSlot.transfer(previous, (ServerPlayer) (Object) this);
        }
    }
}
