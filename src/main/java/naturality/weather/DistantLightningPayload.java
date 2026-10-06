package naturality.weather;
import naturality.Naturality;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public record DistantLightningPayload(Identifier dimension,double x,double z,int seaLevel,long seed) implements CustomPacketPayload {
    public static final Type<DistantLightningPayload> TYPE=new Type<>(Naturality.id("distant_lightning"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DistantLightningPayload> CODEC=new StreamCodec<>() {
        public DistantLightningPayload decode(RegistryFriendlyByteBuf b) { return new DistantLightningPayload(b.readIdentifier(),b.readDouble(),b.readDouble(),b.readInt(),b.readLong()); }
        public void encode(RegistryFriendlyByteBuf b,DistantLightningPayload p) { b.writeIdentifier(p.dimension);b.writeDouble(p.x);b.writeDouble(p.z);b.writeInt(p.seaLevel);b.writeLong(p.seed); }
    };
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
