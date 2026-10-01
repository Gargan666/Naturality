package naturality.config;

import naturality.Naturality;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GameplaySettingsPayload(boolean fireWrapping, boolean vanillaPortalEntry, boolean snowWrapping, boolean snowCompaction, boolean weatherThaw, boolean weatherSnowAccumulation) implements CustomPacketPayload {
    public GameplaySettingsPayload(boolean fireWrapping, boolean vanillaPortalEntry) {
        this(fireWrapping, vanillaPortalEntry, true, true, true, true);
    }
    public static final Type<GameplaySettingsPayload> TYPE = new Type<>(Naturality.id("gameplay_settings_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GameplaySettingsPayload> CODEC = new StreamCodec<@org.jspecify.annotations.NonNull RegistryFriendlyByteBuf, @org.jspecify.annotations.NonNull GameplaySettingsPayload>() {
        public GameplaySettingsPayload decode(RegistryFriendlyByteBuf buffer) {
            return new GameplaySettingsPayload(buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
        }
        public void encode(RegistryFriendlyByteBuf buffer, GameplaySettingsPayload settings) {
            buffer.writeBoolean(settings.fireWrapping());
            buffer.writeBoolean(settings.vanillaPortalEntry());
            buffer.writeBoolean(settings.snowWrapping());
            buffer.writeBoolean(settings.snowCompaction());
            buffer.writeBoolean(settings.weatherThaw());
            buffer.writeBoolean(settings.weatherSnowAccumulation());
        }
    };
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
