package naturality.systems.enchanting.bookshelves.mixins;

import naturality.systems.enchanting.bookshelves.BookshelfBooks;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class BookshelfMixins {
    private BookshelfMixins() {}
    @Mixin(BlockEntity.class)
    public static abstract class ContentsSync {
        @Inject(method = "getUpdateTag", at = @At("HEAD"), cancellable = true)
        private void naturality$shelfContents(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
            if ((Object)this instanceof ChiseledBookShelfBlockEntity shelf) cir.setReturnValue(shelf.saveWithoutMetadata(registries));
        }
        @Inject(method = "getUpdatePacket", at = @At("HEAD"), cancellable = true)
        private void naturality$shelfPacket(CallbackInfoReturnable<@Nullable Packet<ClientGamePacketListener>> cir) {
            if ((Object)this instanceof ChiseledBookShelfBlockEntity shelf) cir.setReturnValue(ClientboundBlockEntityDataPacket.create(shelf));
        }
    }
    @Mixin(ChiseledBookShelfBlockEntity.class)
    public static abstract class Changes {
        @Unique private ItemStack naturality$previousBook = ItemStack.EMPTY;
        @Inject(method = "setItem", at = @At("HEAD"))
        private void naturality$beforeBook(int slot, ItemStack item, CallbackInfo ci) {
            var shelf = (ChiseledBookShelfBlockEntity)(Object)this;
            naturality$previousBook = slot >= 0 && slot < shelf.getContainerSize() ? shelf.getItem(slot).copy() : ItemStack.EMPTY;
        }
        @Inject(method = "setItem", at = @At("TAIL"))
        private void naturality$afterBook(int slot, ItemStack item, CallbackInfo ci) {
            BookshelfBooks.placed((ChiseledBookShelfBlockEntity)(Object)this, slot, naturality$previousBook);
            naturality$previousBook = ItemStack.EMPTY;
        }
        @Inject(method = "updateState", at = @At("TAIL"))
        private void naturality$synchronizeBooks(int slot, CallbackInfo ci) {
            BookshelfBooks.changed((ChiseledBookShelfBlockEntity)(Object)this);
        }
    }
}
