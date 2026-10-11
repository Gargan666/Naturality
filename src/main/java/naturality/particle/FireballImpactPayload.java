package naturality.particle;

import naturality.Naturality;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Exact block impact geometry, sent before the corresponding explosion packet. */
public record FireballImpactPayload(Identifier dimension, double x, double y, double z,
        double hitX, double hitY, double hitZ, Direction face) implements CustomPacketPayload {
    public static final Type<FireballImpactPayload> TYPE = new Type<>(Naturality.id("fireball_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FireballImpactPayload> CODEC = new StreamCodec<>() {
        @Override public FireballImpactPayload decode(RegistryFriendlyByteBuf buffer) {
            return new FireballImpactPayload(buffer.readIdentifier(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), Direction.STREAM_CODEC.decode(buffer));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, FireballImpactPayload payload) {
            buffer.writeIdentifier(payload.dimension);
            buffer.writeDouble(payload.x); buffer.writeDouble(payload.y); buffer.writeDouble(payload.z);
            buffer.writeDouble(payload.hitX); buffer.writeDouble(payload.hitY); buffer.writeDouble(payload.hitZ);
            Direction.STREAM_CODEC.encode(buffer, payload.face);
        }
    };
    public static void initialize() { PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
