package naturality.sleep;

import naturality.Naturality;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Visual speed is independent of the server's simulation tick rate. */
public record SleepStatePayload(boolean accelerating, double speed) implements CustomPacketPayload {
    public static final Type<SleepStatePayload> TYPE = new Type<>(Naturality.id("sleep_state_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SleepStatePayload> CODEC = new StreamCodec<>() {
        public SleepStatePayload decode(RegistryFriendlyByteBuf buffer) { return new SleepStatePayload(buffer.readBoolean(), buffer.readDouble()); }
        public void encode(RegistryFriendlyByteBuf buffer, SleepStatePayload payload) { buffer.writeBoolean(payload.accelerating); buffer.writeDouble(payload.speed); }
    };
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
