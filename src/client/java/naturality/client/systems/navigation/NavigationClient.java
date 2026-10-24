package naturality.client.systems.navigation;

import java.util.Locale;
import naturality.bootstrap.ContentRegistration.NaturalityItems;
import naturality.systems.navigation.NavigationInstruments;
import naturality.systems.navigation.NavigationInstruments.NavigationTargetPayload;
import naturality.systems.weather.framework.WeatherSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

public final class NavigationClient {
    private static GlobalPos target;
    private NavigationClient() {}
    public static void initialize() {
        net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties.ID_MAPPER.put(
            naturality.Naturality.id("barometer"), BarometerProperty.MAP_CODEC);
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
        boolean barometer = player.getMainHandItem().is(NaturalityItems.BAROMETER)
            || player.getOffhandItem().is(NaturalityItems.BAROMETER);
        if (client.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof ItemFrame frame) {
            var framedItem = frame.getItem();
            clock |= framedItem.is(Items.CLOCK);
            compass |= framedItem.is(Items.COMPASS);
            barometer |= framedItem.is(NaturalityItems.BAROMETER);
        }
        barometer &= level.dimension().equals(Level.OVERWORLD);
        if (!clock && !compass && !barometer) return null;
        String text = "";
        if (clock) {
            var definition = level.registryAccess().getOrThrow(WorldClocks.OVERWORLD);
            text = NavigationInstruments.time(level.clockManager().getInstance(definition).totalTicks());
        }
        if (compass) {
            var pos = player.blockPosition();
            text += (clock ? " | " : "") + "X: " + pos.getX() + " Y: " + pos.getY() + " Z: " + pos.getZ();
        }
        if (barometer) {
            var weather = WeatherSystem.renderState(level);
            float rain = weather == null ? level.getRainLevel(1) * 25 + level.getThunderLevel(1) * 25 : weather.rain();
            float wind = weather == null ? 0 : weather.wind();
            text += (text.isEmpty() ? "" : " | ") + String.format(Locale.ROOT, "rain %.1f, wind %.1f", rain, wind);
        }
        return Component.literal(text);
    }
}
