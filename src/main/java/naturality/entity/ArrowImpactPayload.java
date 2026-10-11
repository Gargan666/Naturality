package naturality.entity;

import naturality.Naturality;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public record ArrowImpactPayload(Identifier dimension, int entityId, int index, Vec3 offset, Vec3 direction, float bodyYaw) implements CustomPacketPayload {
    public static final Type<ArrowImpactPayload> TYPE = new Type<>(Naturality.id("arrow_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArrowImpactPayload> CODEC = new StreamCodec<>() {
        public ArrowImpactPayload decode(RegistryFriendlyByteBuf b) {
            return new ArrowImpactPayload(b.readIdentifier(), b.readInt(), b.readInt(),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readFloat());
        }
        public void encode(RegistryFriendlyByteBuf b, ArrowImpactPayload p) {
            b.writeIdentifier(p.dimension); b.writeInt(p.entityId); b.writeInt(p.index);
            b.writeDouble(p.offset.x); b.writeDouble(p.offset.y); b.writeDouble(p.offset.z);
            b.writeDouble(p.direction.x); b.writeDouble(p.direction.y); b.writeDouble(p.direction.z); b.writeFloat(p.bodyYaw);
        }
    };
    public static void initialize() { PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC); }
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
