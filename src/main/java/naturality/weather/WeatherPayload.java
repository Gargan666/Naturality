package naturality.weather;

import naturality.Naturality;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record WeatherPayload(Identifier dimension, long worldSession, boolean enabled, WeatherState state) implements CustomPacketPayload {
    public static final Type<WeatherPayload> TYPE = new Type<>(Naturality.id("weather"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WeatherPayload> CODEC = new StreamCodec<@org.jspecify.annotations.NonNull RegistryFriendlyByteBuf, @org.jspecify.annotations.NonNull WeatherPayload>() {
        public WeatherPayload decode(RegistryFriendlyByteBuf b) {
            return new WeatherPayload(b.readIdentifier(), b.readLong(), b.readBoolean(), new WeatherState(b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat()));
        }
        public void encode(RegistryFriendlyByteBuf b, WeatherPayload p) {
            b.writeIdentifier(p.dimension); b.writeLong(p.worldSession); b.writeBoolean(p.enabled);
            b.writeFloat(p.state.rain()); b.writeFloat(p.state.wind()); b.writeFloat(p.state.temperature()); b.writeFloat(p.state.direction());
        }
    };
    /** Convenience constructor for local tests and direct client state injection. */
    public WeatherPayload(Identifier dimension, boolean enabled, WeatherState state) {
        this(dimension, 0, enabled, state);
    }
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
