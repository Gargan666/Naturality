package naturality.portal;
import naturality.Naturality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PortalCrossingPayload(String dimension,int entity,BlockPos pos,Direction.Axis axis,int side,boolean active) implements CustomPacketPayload {
    public static final Type<PortalCrossingPayload> TYPE=new Type<>(Naturality.id("portal_crossing"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PortalCrossingPayload> CODEC=new StreamCodec<@org.jspecify.annotations.NonNull RegistryFriendlyByteBuf, @org.jspecify.annotations.NonNull PortalCrossingPayload>() {
        public PortalCrossingPayload decode(RegistryFriendlyByteBuf b) {return new PortalCrossingPayload(b.readUtf(256),b.readVarInt(),b.readBlockPos(),b.readBoolean()?Direction.Axis.X:Direction.Axis.Z,b.readBoolean()?1:-1,b.readBoolean());}
        public void encode(RegistryFriendlyByteBuf b,PortalCrossingPayload p) {b.writeUtf(p.dimension,256);b.writeVarInt(p.entity);b.writeBlockPos(p.pos);b.writeBoolean(p.axis==Direction.Axis.X);b.writeBoolean(p.side>0);b.writeBoolean(p.active);}
    };
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
