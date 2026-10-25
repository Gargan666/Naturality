package naturality.systems.navigation;

import java.util.Locale;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import naturality.Naturality;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class NavigationInstruments {
    // FastQuit can tick a retiring server while its replacement begins. Only the
    // outer ownership map is shared; each player's sent targets stay on its server thread.
    private static final Map<MinecraftServer, Map<ServerPlayer, GlobalPos>> SENT = Collections.synchronizedMap(new WeakHashMap<>());
    private NavigationInstruments() {}
    public static GlobalPos target(ServerPlayer player) {
        var respawn = player.getRespawnConfig();
        if (respawn != null && respawn.respawnData().dimension().equals(player.level().dimension()))
            return respawn.respawnData().globalPos();
        if (player.level().dimension().equals(Level.OVERWORLD))
            return player.level().getRespawnData().globalPos();
        return GlobalPos.of(player.level().dimension(), BlockPos.ZERO);
    }
    public static String time(long ticks) {
        int minutes = (int)(Math.floorMod(ticks, 24000L) * 1440 / 24000 + 360) % 1440;
        int hour = minutes / 60;
        return String.format(Locale.ROOT, "Day %d | %02d:%02d %s (%02d:%02d)",
            Math.floorDiv(ticks, 24000L), hour % 12 == 0 ? 12 : hour % 12,
            minutes % 60, hour < 12 ? "AM" : "PM", hour, minutes % 60);
    }
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(NavigationInstruments::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(NavigationInstruments::clearServer);
    }
    private static void tick(MinecraftServer server) {
        var sent = SENT.computeIfAbsent(server, _ -> new WeakHashMap<>());
        for (var player : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, NavigationTargetPayload.TYPE)) continue;
            var target = target(player);
            if (!target.equals(sent.get(player))) {
                ServerPlayNetworking.send(player, new NavigationTargetPayload(target));
                sent.put(player, target);
            }
        }
        sent.keySet().removeIf(ServerPlayer::hasDisconnected);
    }
    private static void clearServer(MinecraftServer server) { SENT.remove(server); }

    public record NavigationTargetPayload(GlobalPos target) implements CustomPacketPayload {
        public static final Type<NavigationTargetPayload> TYPE = new Type<>(Naturality.id("navigation_target"));
        public static final StreamCodec<RegistryFriendlyByteBuf, NavigationTargetPayload> CODEC =
            StreamCodec.composite(GlobalPos.STREAM_CODEC, NavigationTargetPayload::target, NavigationTargetPayload::new);
        public static void initialize() { PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC); }
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
