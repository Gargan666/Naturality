package naturality.portal;

import java.util.ArrayList;
import java.util.List;
import naturality.Naturality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PortalOpeningPayload(String dimension, BlockPos min, BlockPos max,
        Direction.Axis axis, List<BlockPos> fires, int elapsed) implements CustomPacketPayload {
    public static final Type<PortalOpeningPayload> TYPE = new Type<>(Naturality.id("portal_opening"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PortalOpeningPayload> CODEC = new StreamCodec<@org.jspecify.annotations.NonNull RegistryFriendlyByteBuf, @org.jspecify.annotations.NonNull PortalOpeningPayload>() {
        @Override public PortalOpeningPayload decode(RegistryFriendlyByteBuf buf) {
            String dimension = buf.readUtf(256);
            BlockPos min = buf.readBlockPos(), max = buf.readBlockPos();
            Direction.Axis axis = buf.readBoolean() ? Direction.Axis.X : Direction.Axis.Z;
            int elapsed = buf.readInt(), count = buf.readVarInt();
            if (count < 0 || count > 441 || max.getY() - min.getY() < 0 || max.getY() - min.getY() > 20
                || max.getX() - min.getX() < 0 || max.getX() - min.getX() > 20
                || max.getZ() - min.getZ() < 0 || max.getZ() - min.getZ() > 20
                || (axis == Direction.Axis.X ? min.getZ() != max.getZ() : min.getX() != max.getX())) {
                throw new IllegalArgumentException("Invalid portal opening bounds");
            }
            List<BlockPos> fires = new ArrayList<>();
            for (int i = 0; i < count; i++) fires.add(buf.readBlockPos());
            return new PortalOpeningPayload(dimension, min, max, axis, List.copyOf(fires), elapsed);
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, PortalOpeningPayload p) {
            buf.writeUtf(p.dimension(), 256);
            buf.writeBlockPos(p.min()); buf.writeBlockPos(p.max());
            buf.writeBoolean(p.axis() == Direction.Axis.X);
            buf.writeInt(p.elapsed()); buf.writeVarInt(p.fires().size());
            p.fires().forEach(buf::writeBlockPos);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
