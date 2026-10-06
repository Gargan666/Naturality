package naturality.weather;

import java.util.Map;
import java.util.UUID;
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
    private static final Map<ServerLevel, WeatherChannel> RAIN_CYCLES = new WeakHashMap<>();
    private static final Map<ServerLevel, WeatherChannel> WIND_CYCLES = new WeakHashMap<>();
    private static final Map<net.minecraft.server.MinecraftServer, Long> SERVER_SESSIONS = new WeakHashMap<>();
    private static final Map<Identifier, ClientWeather> CLIENT = new ConcurrentHashMap<>();
    private static final long CLIENT_BLEND_NANOS = 500_000_000L;
    private static @org.jspecify.annotations.Nullable Long CLIENT_SESSION;
    private WeatherSystem() {}
    public static @org.jspecify.annotations.Nullable WeatherState state(Level level) {
        if (level.isClientSide()) {
            var weather = CLIENT.get(level.dimension().identifier());
            return weather == null ? null : weather.target;
        }
        return level instanceof ServerLevel server && profile(level).enabled ? STATES.get(server) : null;
    }
    /** Frame-time weather visuals blend sparse network snapshots without changing gameplay state. */
    public static @org.jspecify.annotations.Nullable WeatherState renderState(Level level) {
        if (!level.isClientSide()) return state(level);
        var weather = CLIENT.get(level.dimension().identifier());
        return weather == null ? null : weather.at(System.nanoTime());
    }
    public static WeatherProfile profile(Level level) {
        var server = level.getServer();
        return server == null ? DISABLED
            : WeatherWorldData.get(server).profile(level.dimension().identifier().toString());
    }
    private static final WeatherProfile DISABLED = new WeatherProfile(false);
    public static void receive(WeatherPayload p) {
        Long previousSession = CLIENT_SESSION;
        if (previousSession == null || previousSession.longValue() != p.worldSession()) {
            CLIENT.clear();
            CLIENT_SESSION = p.worldSession();
        }
        if (p.enabled()) {
            long now = System.nanoTime();
            var previous = CLIENT.get(p.dimension());
            var from = previous == null ? p.state() : previous.at(now);
            CLIENT.put(p.dimension(), new ClientWeather(from, p.state(), now));
        } else CLIENT.remove(p.dimension());
    }
    public static void clearClient() { CLIENT.clear(); CLIENT_SESSION = null; }
    private record ClientWeather(WeatherState from, WeatherState target, long startedAt) {
        WeatherState at(long now) {
            float progress = Math.clamp((now - startedAt) / (float)CLIENT_BLEND_NANOS, 0, 1);
            return interpolate(from, target, progress);
        }
    }
    public static WeatherState interpolate(WeatherState from, WeatherState to, float progress) {
        float t = Math.clamp(progress, 0, 1);
        t = t * t * (3 - 2 * t);
        return new WeatherState(from.rain() + (to.rain() - from.rain()) * t,
            from.wind() + (to.wind() - from.wind()) * t,
            from.temperature() + (to.temperature() - from.temperature()) * t,
            (from.direction() + angularDistance(from.direction(), to.direction()) * t + 360) % 360);
    }
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
        EndWeatherSystem.initialize();
        PayloadTypeRegistry.clientboundPlay().register(WeatherPayload.TYPE, WeatherPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DistantLightningPayload.TYPE,DistantLightningPayload.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SERVER_SESSIONS.put(server, UUID.randomUUID().getLeastSignificantBits());
            for(var level:server.getAllLevels())restore(level);
            var legacy = NaturalityServerConfig.takeLegacyWeather();
            if (!legacy.isEmpty()) {
                var stored = server.getDataStorage().get(WeatherWorldData.TYPE);
                if (stored == null) WeatherWorldData.get(server).importLegacy(legacy);
                NaturalityServerConfig.get().save();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear(); CLOCKS.clear(); RAIN_CYCLES.clear(); WIND_CYCLES.clear();
            SERVER_SESSIONS.remove(server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, _, server) -> {
            for (var level : server.getAllLevels()) send(handler.player, level);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var level : server.getAllLevels()) {
                advance(level);
                DistantLightning.tick(level);
                snapshot(level);
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
            long session = SERVER_SESSIONS.computeIfAbsent(level.getServer(), _ -> UUID.randomUUID().getLeastSignificantBits());
            ServerPlayNetworking.send(player, new WeatherPayload(level.dimension().identifier(), session, current != null,
                current == null ? WeatherState.CLEAR : current));
        }
    }
    private static void advance(ServerLevel level) {
        var p = profile(level);
        if (!p.enabled) { STATES.remove(level); CLOCKS.remove(level); RAIN_CYCLES.remove(level); WIND_CYCLES.remove(level); return; }
        long seed = level.getSeed() ^ level.dimension().identifier().hashCode();
        var old = STATES.get(level);
        boolean cycle = level.getGameRules().get(GameRules.ADVANCE_WEATHER) || old == null;
        long time = CLOCKS.getOrDefault(level, level.getGameTime());
        if (cycle) time++;
        CLOCKS.put(level, time);
        var rainCycle = RAIN_CYCLES.computeIfAbsent(level, _ -> new WeatherChannel(seed ^ 0x5241494EL));
        var windCycle = WIND_CYCLES.computeIfAbsent(level, _ -> new WeatherChannel(seed ^ 0x57494E44L));
        float rain = rainCycle.tick(cycle, time, 0, 100,
            1 - Math.pow(.6, 1.0 / 12000), 6000, 18000, 5000);
        float wind = windCycle.tick(cycle, time, p.minWind, p.maxWind,
            1 - Math.pow(.65, 1.0 / 6000), 8000, 24000, 4200);
        float temp = old == null ? 50 : automaticTemperature(p, seed, time);
        float direction = automaticDirection(seed, time);
        if (!cycle && old != null) { wind = old.wind(); temp = old.temperature(); direction = old.direction(); }
        // The advance_weather gamerule pauses these autonomous slider cycles.
        // /weather edits independent profile overrides, not the vanilla clock.
        float targetDirection = p.overrideDirection ? p.direction : direction;
        if (old != null) targetDirection = cycle ? approachDirection(old.direction(), targetDirection, .5F) : old.direction();
        if (old == null) {
            STATES.put(level, new WeatherState(p.overrideRain ? p.rain : 0,
                p.overrideWind ? p.wind : 0, p.overrideTemperature ? p.temperature : 50, targetDirection));
            return;
        }
        var target = new WeatherState(p.overrideRain ? p.rain : rain, p.overrideWind ? p.wind : wind,
            p.overrideTemperature ? p.temperature : temp, targetDirection);
        if (old != null) target = new WeatherState(approach(old.rain(), target.rain(), .5F),
            approach(old.wind(), target.wind(), .5F), approach(old.temperature(), target.temperature(), p.overrideTemperature ? .25F : .005F), targetDirection);
        STATES.put(level, target);
    }
    private static String key(ServerLevel level) { return "weather/"+level.dimension().identifier(); }
    private static void restore(ServerLevel level) {
        var data=EnvironmentWorldData.get(level.getServer());
        var saved=data.read(key(level));
        if(saved.isEmpty())return;
        STATES.put(level,new WeatherState(EnvironmentWorldData.number(saved,"rain"),EnvironmentWorldData.number(saved,"wind"),
            EnvironmentWorldData.number(saved,"temperature"),EnvironmentWorldData.number(saved,"direction")));
        CLOCKS.put(level,saved.getOrDefault("clock",0L));
        long seed=level.getSeed() ^ level.dimension().identifier().hashCode();
        var rain=new WeatherChannel(seed ^ 0x5241494EL);rain.restore(data.read(key(level)+"/rain"));RAIN_CYCLES.put(level,rain);
        var wind=new WeatherChannel(seed ^ 0x57494E44L);wind.restore(data.read(key(level)+"/wind"));WIND_CYCLES.put(level,wind);
    }
    private static void snapshot(ServerLevel level) {
        var state=STATES.get(level);if(state==null)return;
        var data=EnvironmentWorldData.get(level.getServer());
        data.write(key(level),Map.of("rain",EnvironmentWorldData.bits(state.rain()),"wind",EnvironmentWorldData.bits(state.wind()),
            "temperature",EnvironmentWorldData.bits(state.temperature()),"direction",EnvironmentWorldData.bits(state.direction()),
            "clock",CLOCKS.getOrDefault(level,0L)));
        var rain=RAIN_CYCLES.get(level);if(rain!=null)data.write(key(level)+"/rain",rain.snapshot());
        var wind=WIND_CYCLES.get(level);if(wind!=null)data.write(key(level)+"/wind",wind.snapshot());
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
    /** Slowly changing deterministic random heading, sampled on a wrapped 0..360 circle. */
    public static float automaticDirection(long seed, long weatherTime) {
        return 360 * noise(seed + 311, Math.max(0, weatherTime), 23000);
    }
    private static float sample(long seed, long cell) {
        long x = seed + cell * 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return ((x ^ (x >>> 31)) >>> 40) / (float)0xFFFFFF;
    }

    /** A slider stays at zero until its own weather event starts, then wanders until it ends. */
    private static final class WeatherChannel {
        private final SavedRandom random;
        private final long seed;
        private boolean active;
        private int remaining;
        private int age;
        private float value;
        private float initialBoost;

        private WeatherChannel(long seed) { this.seed = seed; random = new SavedRandom(seed); }
        private Map<String,Long> snapshot() {
            return Map.of("random",random.state(),"active",active?1L:0L,"remaining",(long)remaining,"age",(long)age,
                "value",EnvironmentWorldData.bits(value),"boost",EnvironmentWorldData.bits(initialBoost));
        }
        private void restore(Map<String,Long> data) {
            if(data.isEmpty())return;
            random.restore(data.getOrDefault("random",random.state()));active=data.getOrDefault("active",0L)!=0;
            remaining=data.getOrDefault("remaining",0L).intValue();age=data.getOrDefault("age",0L).intValue();
            value=EnvironmentWorldData.number(data,"value");initialBoost=EnvironmentWorldData.number(data,"boost");
        }

        private float tick(boolean advance, long time, int minimum, int maximum, double startChance,
                int minimumDuration, int maximumDuration, int noisePeriod) {
            if (!advance) return value;
            if (active) {
                age++;
                if (--remaining <= 0) active = false;
            } else if (value <= .01F && random.nextDouble() < startChance) {
                active = true;
                age = 0;
                remaining = minimumDuration + random.nextInt(maximumDuration - minimumDuration + 1);
                initialBoost = minimum + (maximum - minimum) * (.18F + random.nextFloat() * .14F);
                value = Math.max(value, initialBoost);
            }
            float target = 0;
            if (active) {
                float randomized = minimum + (maximum - minimum) * noise(seed, time, noisePeriod);
                float transition = Math.clamp(age / 1200F, 0, 1);
                transition = transition * transition * (3 - 2 * transition);
                target = initialBoost + (randomized - initialBoost) * transition;
            }
            value += Math.clamp(target - value, -.04F, .04F);
            return value;
        }
    }
}


