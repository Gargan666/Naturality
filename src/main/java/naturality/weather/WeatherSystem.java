package naturality.weather;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import naturality.config.NaturalityServerConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.gamerules.GameRules;

/** Server owns evolution; common queries keep gameplay and rendering on the same climate. */
public final class WeatherSystem {
    private static final long SEASON_LENGTH = 72000; // Three Minecraft days.
    private static final long SEASON_HOLD = 48000; // Two steady days, then one transition day.
    private static final Map<ServerLevel, WeatherState> STATES = new WeakHashMap<>();
    private static final Map<ServerLevel, Long> CLOCKS = new WeakHashMap<>();
    private static final Map<Identifier, WeatherState> CLIENT = new ConcurrentHashMap<>();
    private WeatherSystem() {}
    public static @org.jspecify.annotations.Nullable WeatherState state(Level level) {
        if (level.isClientSide()) return CLIENT.get(level.dimension().identifier());
        return level instanceof ServerLevel server && profile(level).enabled ? STATES.get(server) : null;
    }
    public static WeatherProfile profile(Level level) {
        var server = level.getServer();
        return server == null ? DISABLED
            : WeatherWorldData.get(server).profile(level.dimension().identifier().toString());
    }
    private static final WeatherProfile DISABLED = new WeatherProfile(false);
    public static void receive(WeatherPayload p) {
        if (p.enabled()) CLIENT.put(p.dimension(), p.state()); else CLIENT.remove(p.dimension());
    }
    public static void clearClient() { CLIENT.clear(); }
    public static Biome.Precipitation precipitation(Level level, Biome biome, BlockPos pos) {
        var normal = biome.getPrecipitationAt(pos, level.getSeaLevel());
        var state = state(level);
        if (state == null || normal != Biome.Precipitation.RAIN) return normal;
        // Arid biomes have already returned NONE; naturally cold/elevated biomes retain SNOW.
        // Wet tropical biomes stay rainy; temperate biomes freeze as the offset falls.
        return biome.getBaseTemperature() < 0.95F
            && biome.getBaseTemperature() + (state.temperature() - 50) / 40 < 0.15F
                ? Biome.Precipitation.SNOW : normal;
    }
    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(WeatherPayload.TYPE, WeatherPayload.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var legacy = NaturalityServerConfig.takeLegacyWeather();
            if (!legacy.isEmpty()) {
                var stored = server.getDataStorage().get(WeatherWorldData.TYPE);
                if (stored == null) WeatherWorldData.get(server).importLegacy(legacy);
                NaturalityServerConfig.get().save();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> { STATES.clear(); CLOCKS.clear(); });
        ServerPlayConnectionEvents.JOIN.register((handler, _, server) -> {
            for (var level : server.getAllLevels()) send(handler.player, level);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var level : server.getAllLevels()) {
                advance(level);
                if (server.getTickCount() % 10 == 0)
                    for (var player : server.getPlayerList().getPlayers()) send(player, level);
            }
        });
    }
    public static void saveProfile(ServerLevel level) {
        var server = level.getServer();
        if (server == null) return;
        WeatherWorldData.get(server).setDirty();
        server.getDataStorage().scheduleSave();
    }
    private static void send(net.minecraft.server.level.ServerPlayer player, ServerLevel level) {
        if (ServerPlayNetworking.canSend(player, WeatherPayload.TYPE)) {
            var current = state(level);
            ServerPlayNetworking.send(player, new WeatherPayload(level.dimension().identifier(), current != null,
                current == null ? WeatherState.CLEAR : current));
        }
    }
    private static void advance(ServerLevel level) {
        var p = profile(level);
        if (!p.enabled) { STATES.remove(level); CLOCKS.remove(level); return; }
        long seed = level.getSeed() ^ level.dimension().identifier().hashCode();
        var old = STATES.get(level);
        boolean cycle = level.getGameRules().get(GameRules.ADVANCE_WEATHER) || old == null;
        long time = CLOCKS.getOrDefault(level, level.getGameTime());
        if (cycle) time++;
        CLOCKS.put(level, time);
        var vanilla = level.getWeatherData();
        float rain = vanilla.isRaining() ? (vanilla.isThundering()
            ? 50 + 50 * noise(seed, time, 4700) : 5 + 44 * noise(seed, time, 3100)) : 0;
        float wind = p.minWind + (p.maxWind - p.minWind) * noise(seed + 71, time, 5300);
        float temp = automaticTemperature(p, seed, time);
        float direction = 360 * noise(seed + 311, time, 23000);
        if (!cycle && old != null) { wind = old.wind(); temp = old.temperature(); direction = old.direction(); }
        // Retain the vanilla wet/dry clock, sleeping and advance_weather behavior.
        // /weather now edits independent profile overrides instead of that clock.
        // Wet periods contain continuous intensity, not an enumerated event type.
        float targetDirection = p.overrideDirection ? p.direction : direction;
        if (old != null) targetDirection = cycle ? approachDirection(old.direction(), targetDirection, .5F) : old.direction();
        var target = new WeatherState(p.overrideRain ? p.rain : rain, p.overrideWind ? p.wind : wind,
            p.overrideTemperature ? p.temperature : temp, targetDirection);
        if (old != null) target = new WeatherState(approach(old.rain(), target.rain(), .5F),
            approach(old.wind(), target.wind(), .5F), approach(old.temperature(), target.temperature(), p.overrideTemperature ? .25F : .005F), targetDirection);
        STATES.put(level, target);
    }
    private static float approach(float from, float to, float step) { return from + Math.clamp(to - from, -step, step); }
    public static float angularDistance(float from, float to) { return (to - from + 540) % 360 - 180; }
    public static float approachDirection(float from, float to, float step) {
        return (from + Math.clamp(angularDistance(from,to),-step,step) + 360) % 360;
    }

    /** Long climate plateaus followed by smooth transitions, driven by weather time.
     * The first plateau is always 50; overrides bypass this seasonal schedule. */
    public static float automaticTemperature(WeatherProfile profile, long seed, long weatherTime) {
        long time = Math.max(0, weatherTime);
        long season = time / SEASON_LENGTH;
        float t = Math.clamp((time % SEASON_LENGTH - SEASON_HOLD)
            / (float)(SEASON_LENGTH - SEASON_HOLD), 0, 1);
        t = t * t * (3 - 2 * t);
        float from = seasonalTarget(profile, seed, season);
        float to = seasonalTarget(profile, seed, season + 1);
        return from + (to - from) * t;
    }
    private static float seasonalTarget(WeatherProfile profile, long seed, long season) {
        if (season == 0) return 50;
        // Small seed-dependent differences between years, without rapid daily noise.
        float variation = sample(seed + 137, season) * .1F;
        float fraction = switch ((int)(season % 4)) {
            case 1 -> .9F + variation; // Warm season.
            case 3 -> variation;       // Cold season.
            default -> .5F + variation - .05F;
        };
        return profile.minTemperature + (profile.maxTemperature - profile.minTemperature) * fraction;
    }
    private static float noise(long seed, long tick, int period) {
        long cell = Math.floorDiv(tick, period);
        float t = Math.floorMod(tick, period) / (float)period;
        t = t * t * (3 - 2 * t);
        return sample(seed, cell) * (1 - t) + sample(seed, cell + 1) * t;
    }
    private static float sample(long seed, long cell) {
        long x = seed + cell * 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return ((x ^ (x >>> 31)) >>> 40) / (float)0xFFFFFF;
    }
}


