package naturality.sky;

import naturality.Naturality;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SkyEventPayload(Identifier dimension, String event, float strength, long appearance) implements CustomPacketPayload {
    public SkyEventPayload(Identifier dimension, String event, float strength) { this(dimension, event, strength, 0); }
    public static final Type<SkyEventPayload> TYPE = new Type<>(Naturality.id("sky_event_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkyEventPayload> CODEC = new StreamCodec<@org.jspecify.annotations.NonNull RegistryFriendlyByteBuf, @org.jspecify.annotations.NonNull SkyEventPayload>() {
        public SkyEventPayload decode(RegistryFriendlyByteBuf b) {
            return new SkyEventPayload(b.readIdentifier(), b.readUtf(64), b.readFloat(), b.readLong());
        }
        public void encode(RegistryFriendlyByteBuf b, SkyEventPayload p) {
            b.writeIdentifier(p.dimension); b.writeUtf(p.event, 64); b.writeFloat(p.strength); b.writeLong(p.appearance);
        }
    };
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
