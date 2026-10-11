package naturality.sleep;

import naturality.Naturality;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Identifies sleep-driven rates separately from administrator tick-rate changes. */
public record SleepStatePayload(boolean accelerating) implements CustomPacketPayload {
    public static final Type<SleepStatePayload> TYPE = new Type<>(Naturality.id("sleep_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SleepStatePayload> CODEC = new StreamCodec<>() {
        public SleepStatePayload decode(RegistryFriendlyByteBuf buffer) { return new SleepStatePayload(buffer.readBoolean()); }
        public void encode(RegistryFriendlyByteBuf buffer, SleepStatePayload payload) { buffer.writeBoolean(payload.accelerating); }
    };
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
