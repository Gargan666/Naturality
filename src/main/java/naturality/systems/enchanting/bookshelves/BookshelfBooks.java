package naturality.systems.enchanting.bookshelves;

import naturality.Naturality;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;

/** Shelf contents use native synchronization; placement pulses are separate events. */
@org.jspecify.annotations.NullMarked
public final class BookshelfBooks {
    private BookshelfBooks() {}
    public record Placement(BlockPos pos, int slot) implements CustomPacketPayload {
        public static final Type<Placement> TYPE = new Type<>(Naturality.id("bookshelf_book_placement"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Placement> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, Placement::pos, ByteBufCodecs.VAR_INT, Placement::slot, Placement::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void initialize() { PayloadTypeRegistry.clientboundPlay().register(Placement.TYPE, Placement.CODEC); }

    public static void changed(ChiseledBookShelfBlockEntity shelf) {
        if (!(shelf.getLevel() instanceof ServerLevel level)) return;
        var state = shelf.getBlockState();
        // Replacements may leave all occupied-state properties unchanged.
        level.sendBlockUpdated(shelf.getBlockPos(), state, state, 3);
        shelf.setChanged();
    }
    public static void placed(ChiseledBookShelfBlockEntity shelf, int slot, ItemStack previous) {
        if (!(shelf.getLevel() instanceof ServerLevel level) || slot < 0 || slot >= shelf.getContainerSize()) return;
        var current = shelf.getItem(slot);
        if (current.isEmpty() || !current.hasFoil() || ItemStack.matches(previous, current)) return;
        for (var player : PlayerLookup.tracking(level, shelf.getBlockPos()))
            ServerPlayNetworking.send(player, new Placement(shelf.getBlockPos(), slot));
    }
}
