package naturality.sky;

import java.util.*;
import naturality.config.NaturalityServerConfig;
import naturality.weather.WeatherSystem;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;

public final class SkyEvents {
    private static final Map<ServerLevel, Map<SkyEventType, SkyEventCycle>> STATES = new WeakHashMap<>();
    private static final Map<ServerLevel, RainbowCycle> RAINBOWS = new WeakHashMap<>();
    private SkyEvents() {}
    public static Map<String, Map<String, SkyEventSettings>> defaults() {
        var dimensions = new java.util.concurrent.ConcurrentHashMap<String, Map<String, SkyEventSettings>>();
        for (String id : List.of("minecraft:overworld", "minecraft:the_end")) {
            var events = new java.util.concurrent.ConcurrentHashMap<String, SkyEventSettings>();
            for (var type : SkyEventType.pool(id)) events.put(type.id, new SkyEventSettings());
            dimensions.put(id, events);
        }
        return dimensions;
    }
    public static float strength(ServerLevel level, SkyEventType type) {
        String id = level.dimension().identifier().toString();
        if (!SkyEventType.pool(id).contains(type)) return 0;
        if (type == SkyEventType.RAINBOW && (isNight(level) || !RainbowCycle.validRain(rainfall(level)))) return 0;
        var settings = NaturalityServerConfig.get().skyEvents.get(id).get(type.id);
        if (settings.override) return Math.clamp(settings.strength, 0, 20);
        if (type == SkyEventType.RAINBOW) {
            var cycle = RAINBOWS.get(level);
            return cycle == null ? 0 : cycle.strength();
        }
        var cycles = STATES.get(level);
        return cycles == null || !cycles.containsKey(type) ? 0 : cycles.get(type).strength();
    }
    static SkyEventCycle cycle(ServerLevel level, SkyEventType type) {
        var cycles = STATES.get(level);
        return cycles == null ? null : cycles.get(type);
    }
    static RainbowCycle rainbowCycle(ServerLevel level) { return RAINBOWS.get(level); }
    public static float rainfall(ServerLevel level) {
        var weather = WeatherSystem.state(level);
        return weather == null ? level.getRainLevel(1F) * 10F : weather.rain();
    }
    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(SkyEventPayload.TYPE, SkyEventPayload.CODEC);
        SkyEventCommand.initialize();
        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> { STATES.clear(); RAINBOWS.clear(); AuroraEvents.clear(); });
        ServerPlayConnectionEvents.JOIN.register((handler, _, server) -> {
            for (var level : server.getAllLevels()) send(handler.player, level);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var level : server.getAllLevels()) {
                var pool = SkyEventType.pool(level.dimension().identifier().toString());
                if (pool.isEmpty()) continue;
                if (pool.contains(SkyEventType.AURORA_BOREALIS)) AuroraEvents.tick(level);
                if (pool.contains(SkyEventType.RAINBOW)) RAINBOWS.computeIfAbsent(level, _ -> new RainbowCycle(
                    level.getSeed() ^ level.getGameTime() ^ SkyEventType.RAINBOW.id.hashCode()))
                    .tick(level.getGameRules().get(GameRules.ADVANCE_WEATHER), !isNight(level), rainfall(level));
                var cycles = STATES.computeIfAbsent(level, _ -> new EnumMap<>(SkyEventType.class));
                for (var type : pool) if (type == SkyEventType.METEOR_SHOWER) cycles.computeIfAbsent(type, _ -> new SkyEventCycle(
                    level.getSeed() ^ level.getGameTime() ^ type.id.hashCode()))
                    .tick(level.getGameRules().get(GameRules.ADVANCE_WEATHER), isNight(level));
                if (server.getTickCount() % 10 == 0)
                    for (var player : server.getPlayerList().getPlayers()) send(player, level);
            }
        });
    }
    public static boolean isNight(net.minecraft.world.level.Level level) {
        long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
        return time >= 13000 && time < 23000;
    }
    private static void send(ServerPlayer player, ServerLevel level) {
        if (!ServerPlayNetworking.canSend(player, SkyEventPayload.TYPE)) return;
        for (var type : SkyEventType.pool(level.dimension().identifier().toString())) {
            float value = strength(level, type);
            if (type == SkyEventType.AURORA_BOREALIS
                    && !NaturalityServerConfig.get().skyEvents.get(level.dimension().identifier().toString()).get(type.id).override)
                value = player.level() == level ? AuroraEvents.strength(level, player.blockPosition()) : 0;
            ServerPlayNetworking.send(player, new SkyEventPayload(level.dimension().identifier(), type.id, value));
        }
    }
}

