package naturality.weather;

import naturality.Naturality;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record EndWeatherPayload(long session, EndWeatherState state) implements CustomPacketPayload {
    public static final Type<EndWeatherPayload> TYPE = new Type<>(Naturality.id("end_weather"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EndWeatherPayload> CODEC = new StreamCodec<>() {
        public EndWeatherPayload decode(RegistryFriendlyByteBuf b) {
            return new EndWeatherPayload(b.readLong(),new EndWeatherState(b.readFloat(),b.readVarInt(),b.readFloat()));
        }
        public void encode(RegistryFriendlyByteBuf b, EndWeatherPayload p) {
            b.writeLong(p.session); b.writeFloat(p.state.gravity()); b.writeVarInt(p.state.starfall());b.writeFloat(p.state.rise());
        }
    };
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
