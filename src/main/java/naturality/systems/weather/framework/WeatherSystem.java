package naturality.systems.weather.framework;

import naturality.systems.weather.overworld.lightning.DistantLightning;
import naturality.systems.weather.overworld.lightning.DistantLightning.DistantLightningPayload;
import naturality.api.worlds.EnvironmentWorldData;
import naturality.api.worlds.PreviewEnvironment;
import naturality.systems.weather.end.framework.EndWeatherSystem;
import naturality.api.random.SavedRandom;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import naturality.config.gameplay.GameplaySettings.NaturalityServerConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.gamerules.GameRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import naturality.Naturality;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server owns evolution; common queries keep gameplay and rendering on the same climate. */
@org.jspecify.annotations.NullMarked
public final class WeatherSystem {
    // Built-in channel registrations; the items own their cycle parameters.
    private static final ChannelDefinition RAIN = naturality.systems.weather.overworld.rain.RainWeather.CYCLE;
    private static final ChannelDefinition WIND = naturality.systems.weather.wind.WindPush.CYCLE;

    public record ChannelDefinition(long seedSalt, double startChance, int minimumDuration,
            int maximumDuration, int noisePeriod, java.util.function.ToIntFunction<WeatherProfile> minimum,
            java.util.function.ToIntFunction<WeatherProfile> maximum) {
        private float tick(WeatherChannel channel, boolean advance, long time, WeatherProfile profile, int rate) {
            return channel.tick(advance, time, minimum.applyAsInt(profile), maximum.applyAsInt(profile),
                startChance, minimumDuration, maximumDuration, noisePeriod, rate);
        }
    }

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
        if (!naturality.config.startup.FeatureConfig.Feature.WEATHER_CYCLES.enabled()) return DISABLED;
        var server = java.util.Objects.requireNonNull(level.getServer());
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
            (from.direction() + angularDistance(from.direction(), to.direction()) * t + 360) % 360, to.regularWindOnly());
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
        if (!naturality.config.startup.FeatureConfig.Feature.WEATHER_CYCLES.enabled()) return;
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
                if (!PreviewEnvironment.frozen(server)) DistantLightning.tick(level);
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
            long session = SERVER_SESSIONS.computeIfAbsent(java.util.Objects.requireNonNull(level.getServer()), _ -> UUID.randomUUID().getLeastSignificantBits());
            ServerPlayNetworking.send(player, new WeatherPayload(level.dimension().identifier(), session, current != null,
                current == null ? WeatherState.CLEAR : current));
        }
    }
    private static void advance(ServerLevel level) {
        var p = profile(level);
        if (!p.enabled) { STATES.remove(level); CLOCKS.remove(level); RAIN_CYCLES.remove(level); WIND_CYCLES.remove(level); return; }
        long seed = level.getSeed() ^ level.dimension().identifier().hashCode();
        var old = STATES.get(level);
        if (old != null && PreviewEnvironment.frozen(java.util.Objects.requireNonNull(level.getServer()))) return;
        int rate = PreviewEnvironment.rate(java.util.Objects.requireNonNull(level.getServer()));
        boolean cycle = level.getGameRules().get(GameRules.ADVANCE_WEATHER) || old == null;
        long time = CLOCKS.getOrDefault(level, level.getGameTime());
        if (cycle) time += rate;
        CLOCKS.put(level, time);
        var rainCycle = RAIN_CYCLES.computeIfAbsent(level, _ -> new WeatherChannel(seed ^ RAIN.seedSalt()));
        var windCycle = WIND_CYCLES.computeIfAbsent(level, _ -> new WeatherChannel(seed ^ WIND.seedSalt()));
        float rain = RAIN.tick(rainCycle, cycle, time, p, rate);
        float wind = WIND.tick(windCycle, cycle, time, p, rate);

        float temp = old == null ? 50 : automaticTemperature(p, seed, time);
        float direction = automaticDirection(seed, time);
        if (!cycle && old != null) { wind = old.wind(); temp = old.temperature(); direction = old.direction(); }
        // The advance_weather gamerule pauses these autonomous slider cycles.
        // /weather edits independent profile overrides, not the vanilla clock.
        float targetDirection = p.overrideDirection ? p.direction : direction;
        if (old != null) targetDirection = cycle ? approachDirection(old.direction(), targetDirection, .5F * rate) : old.direction();
        if (old == null) {
            STATES.put(level, new WeatherState(p.overrideRain ? p.rain : 0,
                p.overrideWind ? p.wind : 0, p.overrideTemperature ? p.temperature : 50, targetDirection));
            if (level.dimension().equals(Level.NETHER)) STATES.computeIfPresent(level, (_, state) -> naturality.systems.weather.wind.WindPush.netherState(state));
            return;
        }
        var target = new WeatherState(p.overrideRain ? p.rain : rain, p.overrideWind ? p.wind : wind,
            p.overrideTemperature ? p.temperature : temp, targetDirection);
        if (old != null) target = new WeatherState(approach(old.rain(), target.rain(), .5F * rate),
            approach(old.wind(), target.wind(), .5F * rate), approach(old.temperature(), target.temperature(), (p.overrideTemperature ? .25F : .005F) * rate), targetDirection);
        if (level.dimension().equals(Level.NETHER)) target = naturality.systems.weather.wind.WindPush.netherState(target);
        STATES.put(level, target);
    }
    private static String key(ServerLevel level) { return "weather/"+level.dimension().identifier(); }
    private static void restore(ServerLevel level) {
        var data=EnvironmentWorldData.get(java.util.Objects.requireNonNull(level.getServer()));
        var saved=data.read(key(level));
        if(saved.isEmpty())return;
        STATES.put(level,new WeatherState(EnvironmentWorldData.number(saved,"rain"),EnvironmentWorldData.number(saved,"wind"),
            EnvironmentWorldData.number(saved,"temperature"),EnvironmentWorldData.number(saved,"direction")));
        if (level.dimension().equals(Level.NETHER)) STATES.computeIfPresent(level, (_, state) -> naturality.systems.weather.wind.WindPush.netherState(state));
        CLOCKS.put(level,saved.getOrDefault("clock",0L));
        long seed=level.getSeed() ^ level.dimension().identifier().hashCode();
        var rain=new WeatherChannel(seed ^ RAIN.seedSalt());rain.restore(data.read(key(level)+"/rain"));RAIN_CYCLES.put(level,rain);
        var wind=new WeatherChannel(seed ^ WIND.seedSalt());wind.restore(data.read(key(level)+"/wind"));WIND_CYCLES.put(level,wind);
    }
    private static void snapshot(ServerLevel level) {
        var state=STATES.get(level);if(state==null)return;
        var data=EnvironmentWorldData.get(java.util.Objects.requireNonNull(level.getServer()));
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
                int minimumDuration, int maximumDuration, int noisePeriod, int rate) {
            if (!advance) return value;
            if (active) {
                age += rate;
                if (--remaining <= 0) active = false;
            } else if (value <= .01F && random.nextDouble() < PreviewEnvironment.chance(startChance, rate)) {
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
            value += Math.clamp(target - value, -.04F * rate, .04F * rate);
            return value;
        }
    }

    /** Independent channels. Temperature is a climate offset: 50 preserves biome temperature. */
    public record WeatherState(float rain, float wind, float temperature, float direction, boolean regularWindOnly) {
        public WeatherState(float rain, float wind, float temperature, float direction) {
            this(rain, wind, temperature, direction, false);
        }
        public static final WeatherState CLEAR = new WeatherState(0, 0, 50, 0);
        public WeatherState {
            rain = clamp(rain); wind = clamp(wind); temperature = clamp(temperature);
            direction = Float.isFinite(direction) ? direction % 360 + (direction % 360 < 0 ? 360 : 0) : 0;
        }
        private static float clamp(float value) { return Float.isFinite(value) ? Math.clamp(value, 0, 100) : 50; }
        public float rainLevel() { return Math.min(1, rain / 25); }
        public float thunderLevel() { return Math.clamp((rain - 25) / 25, 0, 1); }
        public float precipitationDensity() {
            float heavy = Math.max(0, (rain - 50) / 50);
            return rain <= 50 ? rain / 25 : 2 + 6 * heavy * heavy * heavy;
        }
        /** Heavy rain fog eases in from slider 80, reaching half strength at 90 and full at 100. */
        public float heavyRainFog() {
            float t = Math.clamp((rain - 80) / 20, 0, 1);
            return t * t * (3 - 2 * t);
        }
        /** Keep indoor rain haze broad and faint; fully exposed rain ends at 15 blocks. */
        public float heavyRainFogEnd(float outdoorExposure) {
            float strength = heavyRainFog() * Math.clamp(outdoorExposure, 0, 1);
            return 100 - 85 * strength;
        }
        /** Heavy rain sprite likelihood grows with fog strength and distance into the fog. */
        public float heavyRainParticleChance(double distance) {
            return heavyRainFog() * Math.clamp((float)(distance / 15.0), 0, 1);
        }
        /** Snow accelerates smoothly through severe winds, reaching four times its calm fall speed. */
        public float snowFallMultiplier() {
            float t=Math.clamp((wind-80)/20,0,1);
            return 1+3*t*t*(3-2*t);
        }
        /** Probability of retaining a vanilla ground impact; does not alter rain sounds. */
        public float groundImpactChance() { float t = rain / 100; return t * t; }
        /** Ordinary wind scales linearly; dimensions allowing severe wind rise sharply above 75. */
        public float windEffect() {
            if (regularWindOnly || wind <= 75) return wind / 75;
            float severe = (wind - 75) / 25;
            return 1 + 2 * severe * severe;
        }
        public float windX() { return (float)Math.cos(Math.toRadians(direction)) * windEffect(); }
        public float windZ() { return (float)Math.sin(Math.toRadians(direction)) * windEffect(); }
        public float weakWindGain() { return wind / 100 * (1 - strongBlend()); }
        public float strongWindGain() { return wind / 100 * strongBlend(); }
        /** Shared severe-mode blend for geometry and shaders; Nether only increases ordinary sway. */
        public float severeWindBlend() {
            if (regularWindOnly) return 0;
            float t = Math.clamp((wind - 80) / 20, 0, 1);
            return t * t * (3 - 2 * t);
        }
        private float strongBlend() {
            if (regularWindOnly) return 0;
            float t = Math.clamp((wind - 30) / 50, 0, 1);
            return t * t * (3 - 2 * t);
        }
    }

    /** Per-world dimension pool/ranges and independently selectable manual channels. */
    public static final class WeatherProfile {
        public static final Codec<WeatherProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled", false).forGetter(p -> p.enabled),
            Codec.BOOL.optionalFieldOf("overrideRain", false).forGetter(p -> p.overrideRain),
            Codec.BOOL.optionalFieldOf("overrideWind", false).forGetter(p -> p.overrideWind),
            Codec.BOOL.optionalFieldOf("overrideTemperature", false).forGetter(p -> p.overrideTemperature),
            Codec.BOOL.optionalFieldOf("overrideDirection", false).forGetter(p -> p.overrideDirection),
            Codec.INT.optionalFieldOf("rain", 25).forGetter(p -> p.rain),
            Codec.INT.optionalFieldOf("wind", 0).forGetter(p -> p.wind),
            Codec.INT.optionalFieldOf("temperature", 50).forGetter(p -> p.temperature),
            Codec.INT.optionalFieldOf("direction", 0).forGetter(p -> p.direction),
            Codec.INT.optionalFieldOf("minWind", 0).forGetter(p -> p.minWind),
            Codec.INT.optionalFieldOf("maxWind", 100).forGetter(p -> p.maxWind),
            Codec.INT.optionalFieldOf("minTemperature", 15).forGetter(p -> p.minTemperature),
            Codec.INT.optionalFieldOf("maxTemperature", 75).forGetter(p -> p.maxTemperature)
        ).apply(instance, WeatherProfile::new));

        public volatile boolean enabled;
        public volatile boolean overrideRain, overrideWind, overrideTemperature, overrideDirection;
        public volatile int rain = 25, wind = 0, temperature = 50, direction = 0;
        public volatile int minWind = 0, maxWind = 100, minTemperature = 15, maxTemperature = 75;
        public WeatherProfile() { this(false); }
        public WeatherProfile(boolean enabled) { this.enabled = enabled; }
        private WeatherProfile(boolean enabled, boolean overrideRain, boolean overrideWind,
                boolean overrideTemperature, boolean overrideDirection, int rain, int wind,
                int temperature, int direction, int minWind, int maxWind,
                int minTemperature, int maxTemperature) {
            this.enabled = enabled;
            this.overrideRain = overrideRain;
            this.overrideWind = overrideWind;
            this.overrideTemperature = overrideTemperature;
            this.overrideDirection = overrideDirection;
            this.rain = rain;
            this.wind = wind;
            this.temperature = temperature;
            this.direction = direction;
            this.minWind = minWind;
            this.maxWind = maxWind;
            this.minTemperature = minTemperature;
            this.maxTemperature = maxTemperature;
            validate();
        }
        public void validate() {
            rain = Math.clamp(rain, 0, 100); wind = Math.clamp(wind, 0, 100); temperature = Math.clamp(temperature, 0, 100);
            direction = Math.clamp(direction, 0, 360);
            minWind = Math.clamp(minWind, 0, 100); maxWind = Math.clamp(maxWind, minWind, 100);
            minTemperature = Math.clamp(minTemperature, 0, 100); maxTemperature = Math.clamp(maxTemperature, minTemperature, 100);
        }
    }

    public record WeatherPayload(Identifier dimension, long worldSession, boolean enabled, WeatherState state) implements CustomPacketPayload {
        public WeatherPayload {
            // Wind mode follows dimension identity, without changing the persisted or wire format.
            if (dimension.equals(Level.NETHER.identifier())) state = naturality.systems.weather.wind.WindPush.netherState(state);
        }
        public static final Type<WeatherPayload> TYPE = new Type<>(Naturality.id("weather"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WeatherPayload> CODEC = new StreamCodec<@org.jspecify.annotations.NonNull RegistryFriendlyByteBuf, @org.jspecify.annotations.NonNull WeatherPayload>() {
            public WeatherPayload decode(RegistryFriendlyByteBuf b) {
                return new WeatherPayload(b.readIdentifier(), b.readLong(), b.readBoolean(), new WeatherState(b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat()));
            }
            public void encode(RegistryFriendlyByteBuf b, WeatherPayload p) {
                b.writeIdentifier(p.dimension); b.writeLong(p.worldSession); b.writeBoolean(p.enabled);
                b.writeFloat(p.state.rain()); b.writeFloat(p.state.wind()); b.writeFloat(p.state.temperature()); b.writeFloat(p.state.direction());
            }
        };
        /** Convenience constructor for local tests and direct client state injection. */
        public WeatherPayload(Identifier dimension, boolean enabled, WeatherState state) {
            this(dimension, 0, enabled, state);
        }
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** All dimension weather pools stored with one save, rather than the installation config. */
    public static final class WeatherWorldData extends SavedData {
        public static final SavedDataType<WeatherWorldData> TYPE = new SavedDataType<>(
            Naturality.id("weather"), WeatherWorldData::new,
            Codec.unboundedMap(Codec.STRING, WeatherProfile.CODEC).xmap(WeatherWorldData::new, data -> data.profiles),
            DataFixTypes.LEVEL);

        private final ConcurrentHashMap<String, WeatherProfile> profiles;

        public WeatherWorldData() {
            profiles = new ConcurrentHashMap<>();
            profiles.put("minecraft:overworld", new WeatherProfile(true));
            profiles.put("minecraft:the_nether", new WeatherProfile(true));
            profiles.put("minecraft:the_end", new WeatherProfile(false));
        }

        private WeatherWorldData(Map<String, WeatherProfile> profiles) {
            this.profiles = new ConcurrentHashMap<>(profiles);
            if (this.profiles.isEmpty()) this.profiles.putAll(new WeatherWorldData().profiles);
            // Previous releases stored an inert Nether profile; wind now runs here by default.
            var nether = this.profiles.computeIfAbsent("minecraft:the_nether", _ -> new WeatherProfile(true));
            if (!nether.enabled && !nether.overrideRain && !nether.overrideWind
                    && !nether.overrideTemperature && !nether.overrideDirection
                    && nether.minWind == 0 && nether.maxWind == 100
                    && nether.minTemperature == 15 && nether.maxTemperature == 75) {
                nether.enabled = true;
                setDirty();
            }
        }

        public static WeatherWorldData get(MinecraftServer server) {
            return server.getDataStorage().computeIfAbsent(TYPE);
        }

        public WeatherProfile profile(String dimension) {
            WeatherProfile existing = profiles.get(dimension);
            if (existing != null) return existing;
            WeatherProfile created = new WeatherProfile(false);
            WeatherProfile prior = profiles.putIfAbsent(dimension, created);
            if (prior != null) return prior;
            setDirty();
            return created;
        }

        public void importLegacy(@org.jspecify.annotations.Nullable Map<String, WeatherProfile> legacy) {
            if (legacy == null || legacy.isEmpty()) return;
            legacy.forEach((dimension, profile) -> {
                if (dimension != null && profile != null) profiles.put(dimension, profile);
            });
            setDirty();
        }

        public void setProfile(String dimension, WeatherProfile profile) {
            profiles.put(dimension, profile);
            setDirty();
        }

        public Map<String, WeatherProfile> profiles() { return Map.copyOf(profiles); }
    }

    /** Dimension-first commands with completions restricted to that dimension's pool. */
    public static final class WeatherCommands {
        private WeatherCommands() {}
        private static List<String> pool(ServerLevel level) {
            return level.dimension().equals(Level.END) ? List.of("gravity","starfall","rise")
                : List.of("rain","wind","temperature","direction");
        }
        private static CommandSyntaxException error(String text) {
            return new SimpleCommandExceptionType(Component.literal(text)).create();
        }
        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
            var target = Commands.argument("dimension", DimensionArgument.dimension())
                .executes(c -> status(c.getSource(),DimensionArgument.getDimension(c,"dimension")));
            target.then(Commands.literal("status").executes(c -> status(c.getSource(),DimensionArgument.getDimension(c,"dimension"))));
            target.then(Commands.literal("auto").executes(c -> {
                var level = DimensionArgument.getDimension(c,"dimension");
                if (level.dimension().equals(Level.END)) {
                    var cycle=EndWeatherSystem.cycle(level);
                    for(String name:pool(level))cycle.set(name,-1);
                    EndWeatherSystem.save(level);
                } else {
                    var p=WeatherSystem.profile(level);
                    p.overrideRain=p.overrideWind=p.overrideTemperature=p.overrideDirection=false;
                    WeatherSystem.saveProfile(level);
                }
                c.getSource().sendSuccess(() -> Component.literal("Weather in " + level.dimension().identifier()+": all sliders automatic."),true);
                return Command.SINGLE_SUCCESS;
            }));
            target.then(Commands.argument("slider",StringArgumentType.word())
                .suggests((c,b) -> SharedSuggestionProvider.suggest(pool(DimensionArgument.getDimension(c,"dimension")),b))
                .then(Commands.argument("value",StringArgumentType.word())
                    .suggests((_,b) -> SharedSuggestionProvider.suggest(List.of("0","50","100","auto"),b))
                    .executes(WeatherCommands::set)));
            dispatcher.register(Commands.literal("weather").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(target));
        }
        private static int set(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
            var level=DimensionArgument.getDimension(c,"dimension");
            String name=StringArgumentType.getString(c,"slider"), text=StringArgumentType.getString(c,"value");
            if(!pool(level).contains(name))throw error("That slider is not in this dimension's weather pool.");
            boolean auto=text.equals("auto");
            int value=-1;
            if (!auto) {
                try { value=Integer.parseInt(text); } catch(NumberFormatException e) { throw error("Expected a whole-number slider value or auto."); }
                int max=name.equals("direction")?360:100;
                if(value<0 || value>max)throw error("Slider must be between 0 and "+max+".");
            }
            if(level.dimension().equals(Level.END)) {
                EndWeatherSystem.cycle(level).set(name,value);
                EndWeatherSystem.save(level);
            } else {
                var p=WeatherSystem.profile(level);
                switch(name) {
                    case "rain" -> { p.overrideRain=!auto; if(!auto)p.rain=value; }
                    case "wind" -> { p.overrideWind=!auto; if(!auto)p.wind=value; }
                    case "temperature" -> { p.overrideTemperature=!auto; if(!auto)p.temperature=value; }
                    case "direction" -> { p.overrideDirection=!auto; if(!auto)p.direction=value; }
                    default -> throw error("Unknown weather slider.");
                }
                WeatherSystem.saveProfile(level);
            }
            String result=auto?"automatic":"target "+value+" (override)";
            String note=
                !level.dimension().equals(Level.END) && !WeatherSystem.profile(level).enabled?" Weather is disabled in this dimension; settings are saved for later.":"";
            c.getSource().sendSuccess(() -> Component.literal("Weather in "+level.dimension().identifier()+": "+name+" "+result+"."+note),true);
            return Command.SINGLE_SUCCESS;
        }
        private static int status(CommandSourceStack source,ServerLevel level) {
            String text;
            if(level.dimension().equals(Level.END)) {
                var cycle=EndWeatherSystem.cycle(level); var s=cycle.state();
                text=String.format(java.util.Locale.ROOT,"gravity %.1f (%s), starfall %d (%s), rise %.1f (%s).",
                    s.gravity(),mode(cycle.override("gravity")),s.starfall(),mode(cycle.override("starfall")),s.rise(),mode(cycle.override("rise")));
            } else {
                var p=WeatherSystem.profile(level); var s=WeatherSystem.state(level);
                text="rain="+(p.overrideRain?p.rain+" (override)":"auto")+", wind="+(p.overrideWind?p.wind+" (override)":"auto")
                    +", temperature="+(p.overrideTemperature?p.temperature+" (override)":"auto")+", direction="+(p.overrideDirection?p.direction+" (override)":"auto");
                if(s!=null)text+=String.format(java.util.Locale.ROOT,". Current: rain %.1f, wind %.1f, temperature %.1f, direction %.1f",s.rain(),s.wind(),s.temperature(),s.direction());
                if(!p.enabled)text+=". Weather is disabled in this dimension; settings are saved for later.";
            }
            String message="Weather in "+level.dimension().identifier()+": "+text;
            source.sendSuccess(() -> Component.literal(message),false);
            return Command.SINGLE_SUCCESS;
        }
        private static String mode(int override) { return override<0?"auto":"override"; }
    }

    public static final class WeatherShelter {
        private static final int[] DISTANCES = {2, 4, 8};
        private record SurfacePlayers(long tick, boolean present) {}
        private static final java.util.WeakHashMap<net.minecraft.server.level.ServerLevel, SurfacePlayers> players = new java.util.WeakHashMap<>();
        private WeatherShelter() {}

        public static boolean hasSurfacePlayer(net.minecraft.server.level.ServerLevel level) {
            var cached = players.get(level);
            if (cached != null && cached.tick == level.getGameTime()) return cached.present;
            boolean present = level.players().stream().anyMatch(p -> !underground(level, p.getEyePosition(), p));
            players.put(level, new SurfacePlayers(level.getGameTime(), present));
            return present;
        }

        public static boolean underground(Level level, Vec3 eye, Entity entity) {
            BlockPos pos = BlockPos.containing(eye);
            if (!naturality.api.worlds.LoadedChunks.has(level, pos)) return true;
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) - eye.y <= 8
                    || level.canSeeSky(pos)) return false;
            // A nearby, unobstructed opening still counts as being at the surface.
            for (int distance : DISTANCES) for (int direction = 0; direction < 8; direction++) {
                double angle = direction * Math.PI / 4;
                Vec3 sample = eye.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
                BlockPos target = BlockPos.containing(sample);
                if (naturality.api.worlds.LoadedChunks.has(level, target) && level.canSeeSky(target)
                        && level.clip(new ClipContext(eye, sample, ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, entity)).getType() == HitResult.Type.MISS) return false;
            }
            return true;
        }
    }
}
