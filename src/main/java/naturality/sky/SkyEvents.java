package naturality.sky;

import java.util.*;
import naturality.weather.WeatherSystem;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;

public final class SkyEvents {
    private static final Map<ServerLevel, Map<SkyEventType, SkyEventCycle>> STATES = new WeakHashMap<>();
    private static final Map<ServerLevel, RainbowCycle> RAINBOWS = new WeakHashMap<>();
    private static final Map<ServerLevel, ProximityCycle> PROXIMITIES = new WeakHashMap<>();
    private static final Map<ServerLevel, BloodMoonCycle> BLOOD_MOONS = new WeakHashMap<>();
    public static ProximityCycle proximityCycle(ServerLevel level) { return PROXIMITIES.get(level); }
    private SkyEvents() {}
    public static Map<String, Map<String, SkyEventSettings>> defaults() {
        var dimensions = new java.util.concurrent.ConcurrentHashMap<String, Map<String, SkyEventSettings>>();
        for (String id : List.of("minecraft:overworld", "minecraft:the_end")) {
            var events = new java.util.concurrent.ConcurrentHashMap<String, SkyEventSettings>();
            for (var type : SkyEventType.pool(id)) {
                var settings = new SkyEventSettings(); settings.strength = Math.min(settings.strength, type.maxStrength());
                events.put(type.id, settings);
            }
            dimensions.put(id, events);
        }
        return dimensions;
    }
    public static float strength(ServerLevel level, SkyEventType type) {
        String id = level.dimension().identifier().toString();
        if (!SkyEventType.pool(id).contains(type)) return 0;
        if (type == SkyEventType.RAINBOW && (isNight(level) || !RainbowCycle.validRain(rainfall(level)))) return 0;
        var settings = SkyEventWorldData.get(java.util.Objects.requireNonNull(level.getServer())).settings(level, type);
        if (settings.override) return Math.clamp(settings.strength, 0, type.maxStrength());
        if (type == SkyEventType.BLOOD_MOON) {
            var cycle = BLOOD_MOONS.get(level); return cycle == null ? 0 : cycle.strength();
        }
        if (type == SkyEventType.PROXIMITY) {
            var cycle = PROXIMITIES.get(level);
            return cycle == null ? 0 : cycle.strength();
        }
        if (type == SkyEventType.RAINBOW) {
            var cycle = RAINBOWS.get(level);
            return cycle == null ? 0 : cycle.strength();
        }
        var cycles = STATES.get(level);
        return cycles == null || !cycles.containsKey(type) ? 0 : cycles.get(type).strength();
    }
    static @org.jspecify.annotations.Nullable SkyEventCycle cycle(ServerLevel level, SkyEventType type) {
        var cycles = STATES.get(level);
        return cycles == null ? null : cycles.get(type);
    }
    static @org.jspecify.annotations.Nullable RainbowCycle rainbowCycle(ServerLevel level) { return RAINBOWS.get(level); }
    public static float rainfall(ServerLevel level) {
        var weather = WeatherSystem.state(level);
        return weather == null ? level.getRainLevel(1F) * 10F : weather.rain();
    }
    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(SkyEventPayload.TYPE, SkyEventPayload.CODEC);
        if (!naturality.config.Feature.SKY_EVENT_CYCLES.enabled()) return;
        SkyEventCommand.initialize();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            for(var level:server.getAllLevels()) {
                var data=naturality.weather.EnvironmentWorldData.get(server);
                var cycles=STATES.computeIfAbsent(level,_ -> new EnumMap<>(SkyEventType.class));
                for(var type:SkyEventType.pool(level.dimension().identifier().toString())) {
                    var saved=data.read(key(level,type));
                    if(type==SkyEventType.METEOR_SHOWER || type==SkyEventType.END_FLASHES) {
                        long seed = level.getSeed() ^ level.getGameTime() ^ type.id.hashCode();
                        var cycle=type==SkyEventType.END_FLASHES ? SkyEventCycle.endFlashes(seed) : new SkyEventCycle(seed);
                        cycle.restore(saved);cycles.put(type,cycle);
                    } else if(type==SkyEventType.BLOOD_MOON) {
                        var cycle = new BloodMoonCycle(level.getSeed() ^ type.id.hashCode());
                        cycle.restore(saved); BLOOD_MOONS.put(level, cycle);
                    } else if(type==SkyEventType.PROXIMITY) {
                        var cycle = new ProximityCycle(level.getSeed() ^ type.id.hashCode());
                        cycle.restore(saved); PROXIMITIES.put(level, cycle);
                    } else if(type==SkyEventType.RAINBOW) {
                        var cycle=new RainbowCycle(level.getSeed() ^ level.getGameTime() ^ type.id.hashCode());
                        cycle.restore(saved);RAINBOWS.put(level,cycle);
                    }
                }
                AuroraEvents.restore(level);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> { STATES.clear(); RAINBOWS.clear(); PROXIMITIES.clear(); BLOOD_MOONS.clear(); AuroraEvents.clear(); });
        ServerPlayConnectionEvents.JOIN.register((handler, _, server) -> {
            for (var level : server.getAllLevels()) send(handler.player, level);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var level : server.getAllLevels()) {
                if (naturality.weather.PreviewEnvironment.frozen(server)) {
                    if (server.getTickCount() % 10 == 0)
                        for (var player : server.getPlayerList().getPlayers()) send(player, level);
                    continue;
                }
                int startMultiplier = naturality.weather.PreviewEnvironment.rate(server);
                var pool = SkyEventType.pool(level.dimension().identifier().toString());
                if (pool.isEmpty()) continue;
                if (pool.contains(SkyEventType.BLOOD_MOON)) {
                    var cycle = BLOOD_MOONS.computeIfAbsent(level, _ -> new BloodMoonCycle(level.getSeed() ^ SkyEventType.BLOOD_MOON.id.hashCode()));
                    cycle.tick(level.getGameRules().get(GameRules.ADVANCE_WEATHER), isNight(level), startMultiplier);
                    naturality.weather.EnvironmentWorldData.get(server).write(key(level, SkyEventType.BLOOD_MOON), cycle.snapshot());
                }
                if (pool.contains(SkyEventType.PROXIMITY)) {
                    var cycle = PROXIMITIES.computeIfAbsent(level, _ -> new ProximityCycle(level.getSeed()));
                    cycle.tick(level.getGameRules().get(GameRules.ADVANCE_WEATHER), isNight(level), startMultiplier);
                    naturality.weather.EnvironmentWorldData.get(server).write(key(level, SkyEventType.PROXIMITY), cycle.snapshot());
                }
                if (pool.contains(SkyEventType.AURORA_BOREALIS) || pool.contains(SkyEventType.END_AURORA)) AuroraEvents.tick(level);
                if (pool.contains(SkyEventType.RAINBOW)) RAINBOWS.computeIfAbsent(level, _ -> new RainbowCycle(
                    level.getSeed() ^ level.getGameTime() ^ SkyEventType.RAINBOW.id.hashCode()))
                    .tick(level.getGameRules().get(GameRules.ADVANCE_WEATHER), !isNight(level), rainfall(level), startMultiplier);
                var cycles = STATES.computeIfAbsent(level, _ -> new EnumMap<>(SkyEventType.class));
                for (var type : pool) if (type == SkyEventType.METEOR_SHOWER || type == SkyEventType.END_FLASHES) {
                    long seed = level.getSeed() ^ level.getGameTime() ^ type.id.hashCode();
                    cycles.computeIfAbsent(type, _ -> type == SkyEventType.END_FLASHES
                        ? SkyEventCycle.endFlashes(seed) : new SkyEventCycle(seed))
                        .tick(type == SkyEventType.END_FLASHES || level.getGameRules().get(GameRules.ADVANCE_WEATHER),
                            type == SkyEventType.END_FLASHES || isNight(level), startMultiplier);
                }
                if (server.getTickCount() % 10 == 0)
                    for (var player : server.getPlayerList().getPlayers()) send(player, level);
                var data=naturality.weather.EnvironmentWorldData.get(server);
                cycles.forEach((type,cycle) -> data.write(key(level,type),cycle.snapshot()));
                var rainbow=RAINBOWS.get(level);
                if(rainbow!=null)data.write(key(level,SkyEventType.RAINBOW),rainbow.snapshot());
            }
        });
    }
    private static String key(ServerLevel level,SkyEventType type) { return "sky/"+level.dimension().identifier()+"/"+type.id; }
    public static boolean isNight(net.minecraft.world.level.Level level) {
        long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
        return time >= 13000 && time < 23000;
    }
    private static void send(ServerPlayer player, ServerLevel level) {
        if (!ServerPlayNetworking.canSend(player, SkyEventPayload.TYPE)) return;
        for (var type : SkyEventType.pool(level.dimension().identifier().toString())) {
            float value = strength(level, type);
            if ((type == SkyEventType.AURORA_BOREALIS || type == SkyEventType.END_AURORA)
                    && !SkyEventWorldData.get(java.util.Objects.requireNonNull(level.getServer())).settings(level, type).override)
                value = player.level() == level ? AuroraEvents.strength(level, player.blockPosition()) : 0;
            var proximity = PROXIMITIES.get(level);
            ServerPlayNetworking.send(player, new SkyEventPayload(level.dimension().identifier(), type.id, value,
                type == SkyEventType.PROXIMITY && proximity != null ? proximity.appearance() : 0));
        }
    }
}

