package naturality.weather;

import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

public final class EndWeatherSystem {
    private static final String KEY = "weather/minecraft:the_end/gravity_cycle";
    private static final WeakHashMap<ServerLevel, EndWeatherCycle> cycles = new WeakHashMap<>();
    private static long session;
    private record ClientState(long session, EndWeatherState from, EndWeatherState target, long time) {}
    private static volatile ClientState client = new ClientState(0,EndWeatherState.CLEAR,EndWeatherState.CLEAR,0);
    private EndWeatherSystem() {}
    public static EndWeatherCycle cycle(ServerLevel level) {
        return cycles.computeIfAbsent(level, l -> {
            var cycle = new EndWeatherCycle(l.getSeed() ^ 0x454e44475241564cL);
            cycle.restore(EnvironmentWorldData.get(l.getServer()).read(KEY));
            return cycle;
        });
    }
    public static void save(ServerLevel level) { EnvironmentWorldData.get(level.getServer()).write(KEY,cycle(level).snapshot()); }
    public static EndWeatherState state(Level level) {
        if (!level.dimension().equals(Level.END)) return EndWeatherState.CLEAR;
        return level instanceof ServerLevel s ? cycle(s).state() : client.target;
    }
    public static EndWeatherState renderState(Level level) {
        if (!level.dimension().equals(Level.END) || !level.isClientSide()) return state(level);
        var c = client;
        float t = Math.clamp((System.nanoTime()-c.time)/100_000_000F,0,1);
        return new EndWeatherState(c.from.gravity()+(c.target.gravity()-c.from.gravity())*t,c.target.starfall(),c.from.rise()+(c.target.rise()-c.from.rise())*t);
    }
    public static void clearClient() { client = new ClientState(0,EndWeatherState.CLEAR,EndWeatherState.CLEAR,0); }
    public static void receive(EndWeatherPayload p) {
        var previous = client;
        float t = Math.clamp((System.nanoTime()-previous.time)/100_000_000F,0,1);
        var from = p.session() == previous.session ? new EndWeatherState(
            previous.from.gravity()+(previous.target.gravity()-previous.from.gravity())*t,p.state().starfall(),previous.from.rise()+(previous.target.rise()-previous.from.rise())*t) : p.state();
        client = new ClientState(p.session(),from,p.state(),System.nanoTime());
    }
    private static void send(net.minecraft.server.level.ServerPlayer player, ServerLevel level) {
        if (ServerPlayNetworking.canSend(player,EndWeatherPayload.TYPE))
            ServerPlayNetworking.send(player,new EndWeatherPayload(session,cycle(level).state()));
    }
    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(EndWeatherPayload.TYPE,EndWeatherPayload.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> { session = java.util.UUID.randomUUID().getLeastSignificantBits(); });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> cycles.clear());
        ServerPlayConnectionEvents.JOIN.register((handler,sender,s) -> { var end=s.getLevel(Level.END); if(end!=null)send(handler.player,end); });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            var end = s.getLevel(Level.END); if (end == null) return;
            var cycle = cycle(end);
            cycle.tick(!end.players().isEmpty() && end.getGameRules().get(GameRules.ADVANCE_WEATHER));
            naturality.starfall.StarfallWeather.tick(end,cycle.state().starfall());
            save(end);
            if (s.getTickCount()%2 == 0) for (var p:s.getPlayerList().getPlayers()) send(p,end);
        });
    }
}
