package naturality.client.navigation;

import naturality.navigation.NavigationInstruments;
import naturality.navigation.NavigationTargetPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.Level;

public final class NavigationClient {
    private static GlobalPos target;
    private NavigationClient() {}
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(NavigationTargetPayload.TYPE, (payload, _) -> target = payload.target());
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> target = null);
        ClientPlayConnectionEvents.JOIN.register((_, _, _) -> target = null);
    }
    public static GlobalPos target(net.minecraft.client.multiplayer.ClientLevel level) {
        if (target != null && target.dimension().equals(level.dimension())) return target;
        return level.dimension().equals(Level.OVERWORLD) ? level.getRespawnData().globalPos()
            : GlobalPos.of(level.dimension(), BlockPos.ZERO);
    }
    public static Component message() {
        var client = Minecraft.getInstance();
        var level = client.level;
        var player = client.player;
        if (player == null || level == null) return null;
        boolean clock = player.getMainHandItem().is(Items.CLOCK) || player.getOffhandItem().is(Items.CLOCK);
        boolean compass = player.getMainHandItem().is(Items.COMPASS) || player.getOffhandItem().is(Items.COMPASS);
        if (!clock && !compass) return null;
        String text = "";
        if (clock) {
            var definition = level.registryAccess().getOrThrow(WorldClocks.OVERWORLD);
            text = NavigationInstruments.time(level.clockManager().getInstance(definition).totalTicks());
        }
        if (compass) {
            var pos = player.blockPosition();
            text += (clock ? " | " : "") + "X: " + pos.getX() + " Y: " + pos.getY() + " Z: " + pos.getZ();
        }
        return Component.literal(text);
    }
}
