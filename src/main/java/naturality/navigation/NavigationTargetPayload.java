package naturality.navigation;

import naturality.Naturality;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record NavigationTargetPayload(GlobalPos target) implements CustomPacketPayload {
    public static final Type<NavigationTargetPayload> TYPE = new Type<>(Naturality.id("navigation_target"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NavigationTargetPayload> CODEC =
        StreamCodec.composite(GlobalPos.STREAM_CODEC, NavigationTargetPayload::target, NavigationTargetPayload::new);
    public static void initialize() { PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC); }
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
