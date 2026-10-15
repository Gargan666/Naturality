package naturality.systems.skyevents.framework;

import naturality.sky.overworld.rainbow.RainbowCycle;
import naturality.sky.overworld.proximity.ProximityCycle;
import naturality.sky.overworld.bloodmoon.BloodMoonCycle;
import naturality.sky.shared.aurora.AuroraEvents;
import java.util.*;
import naturality.systems.weather.framework.WeatherSystem;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;
import java.util.List;
import naturality.api.random.SavedRandom;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import naturality.Naturality;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.Locale;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;

@org.jspecify.annotations.NullMarked
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
        if (!naturality.config.startup.FeatureConfig.Feature.SKY_EVENT_CYCLES.enabled()) return;
        SkyEventCommand.initialize();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            for(var level:server.getAllLevels()) {
                var data=naturality.api.worlds.EnvironmentWorldData.get(server);
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
                if (naturality.api.worlds.PreviewEnvironment.frozen(server)) {
                    if (server.getTickCount() % 10 == 0)
                        for (var player : server.getPlayerList().getPlayers()) send(player, level);
                    continue;
                }
                int startMultiplier = naturality.api.worlds.PreviewEnvironment.rate(server);
                var pool = SkyEventType.pool(level.dimension().identifier().toString());
                if (pool.isEmpty()) continue;
                if (pool.contains(SkyEventType.BLOOD_MOON)) {
                    var cycle = BLOOD_MOONS.computeIfAbsent(level, _ -> new BloodMoonCycle(level.getSeed() ^ SkyEventType.BLOOD_MOON.id.hashCode()));
                    cycle.tick(level.getGameRules().get(GameRules.ADVANCE_WEATHER), isNight(level), startMultiplier);
                    naturality.api.worlds.EnvironmentWorldData.get(server).write(key(level, SkyEventType.BLOOD_MOON), cycle.snapshot());
                }
                if (pool.contains(SkyEventType.PROXIMITY)) {
                    var cycle = PROXIMITIES.computeIfAbsent(level, _ -> new ProximityCycle(level.getSeed()));
                    cycle.tick(level.getGameRules().get(GameRules.ADVANCE_WEATHER), isNight(level), startMultiplier);
                    naturality.api.worlds.EnvironmentWorldData.get(server).write(key(level, SkyEventType.PROXIMITY), cycle.snapshot());
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
                var data=naturality.api.worlds.EnvironmentWorldData.get(server);
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

    /** Dimension pools are explicit. */
    public enum SkyEventType {
        METEOR_SHOWER("meteor_shower", "Meteor Shower"),
        PROXIMITY("proximity", "Proximity"),
        BLOOD_MOON("blood_moon", "Blood Moon"),
        AURORA_BOREALIS("aurora_borealis", "Aurora Borealis"),
        RAINBOW("rainbow", "Rainbow"),
        END_FLASHES("end_flashes", "End Flashes"),
        END_AURORA("end_aurora", "End Aurora");
        public final String id;
        public final String label;
        public int maxStrength() { return this == BLOOD_MOON ? 1 : this == PROXIMITY ? 7 : 20; }
        SkyEventType(String id, String label) { this.id = id; this.label = label; }
        public static List<SkyEventType> pool(String dimension) {
            if (dimension.equals("minecraft:overworld")) return List.of(METEOR_SHOWER, AURORA_BOREALIS, RAINBOW, PROXIMITY, BLOOD_MOON);
            if (dimension.equals("minecraft:the_end")) return List.of(END_FLASHES, END_AURORA);
            return List.of();
        }
        public static boolean supported(String dimension) {
            return dimension.equals("minecraft:overworld") || dimension.equals("minecraft:the_end");
        }
    }

    /** Random start roll and weather-like active clock, with dimension-specific eligibility. */
    public static final class SkyEventCycle {
        public static final double START_CHANCE_PER_TICK = 1 - Math.pow(.9, 1.0 / 1200);
        public static final double END_FLASH_CHANCE_PER_TICK = 1 - Math.pow(.8, 1.0 / 1200);
        public static final double METEOR_BOOST_CHANCE = .05;
        private final SavedRandom random;
        private final double startChance;
        private final int minDuration, maxDuration;
        private final boolean meteor;
        private int remaining, retarget;
        private int boostClock, boostRemaining;
        private boolean active;
        private float strength, target;
        public SkyEventCycle(long seed) { this(seed, START_CHANCE_PER_TICK, 12000, 24000, true); }
        public static SkyEventCycle endFlashes(long seed) {
            return new SkyEventCycle(seed, END_FLASH_CHANCE_PER_TICK, 1200, 2400, false);
        }
        private SkyEventCycle(long seed, double startChance, int minDuration, int maxDuration, boolean meteor) {
            random = new SavedRandom(seed);
            this.startChance = startChance;
            this.minDuration = minDuration;
            this.maxDuration = maxDuration;
            this.meteor = meteor;
        }
        public java.util.Map<String,Long> snapshot() {
            return java.util.Map.of("random",random.state(),"remaining",(long)remaining,"retarget",(long)retarget,"active",active?1L:0L,"strength",naturality.api.worlds.EnvironmentWorldData.bits(strength),"target",naturality.api.worlds.EnvironmentWorldData.bits(target),"boostClock",(long)boostClock,"boostRemaining",(long)boostRemaining);
        }
        public void restore(java.util.Map<String,Long> data) {
            if(data.isEmpty())return;
            random.restore(data.getOrDefault("random",random.state()));
            remaining=data.getOrDefault("remaining",0L).intValue();
            retarget=data.getOrDefault("retarget",0L).intValue();
            active=data.getOrDefault("active",0L)!=0;
            strength=naturality.api.worlds.EnvironmentWorldData.number(data,"strength");
            target=naturality.api.worlds.EnvironmentWorldData.number(data,"target");
            boostClock=Math.clamp(data.getOrDefault("boostClock",0L).intValue(),0,1199);
            boostRemaining=Math.clamp(data.getOrDefault("boostRemaining",0L).intValue(),0,1200);
            if (meteor && active && boostRemaining == 0) target = Math.clamp(target, 5, 10);
        }
        public float strength() { return strength; }
        public boolean active() { return active; }
        public int remaining() { return remaining; }
        public void tick(boolean advance) {
            tick(advance, true);
        }
        /** A quiet event only rolls to start when its dimension permits starts. */
        public void tick(boolean advance, boolean canStart) {
            tick(advance, canStart, 1);
        }
        public void tick(boolean advance, boolean canStart, int startMultiplier) {
            if (!advance) return;
            if (active && --remaining <= 0) {
                active = false;
                remaining = 0;
                retarget = 0;
                boostClock = boostRemaining = 0;
            } else if (!active && canStart && random.nextDouble() < naturality.api.worlds.PreviewEnvironment.chance(startChance, startMultiplier)) {
                active = true;
                remaining = minDuration + random.nextInt(maxDuration - minDuration + 1);
                retarget = 0;
            }
            if (meteor && active) {
                if (boostRemaining > 0 && --boostRemaining == 0) retarget = 0;
                if (canStart && ++boostClock >= 1200) {
                    boostClock = 0;
                    if (random.nextDouble() < METEOR_BOOST_CHANCE) { boostRemaining = 1200; retarget = 0; }
                }
            }
            if (active && --retarget <= 0) {
                float choice = random.nextFloat();
                target = !meteor ? 1 + choice * 19 : boostRemaining > 0 ? 10 + choice * choice * 10 : 5 + choice * 5;
                retarget = 100 + random.nextInt(501);
            }
            if (!active) target = 0;
            // Exponential easing, snapped only when indistinguishably close.
            strength += (target - strength) * .005F;
            if (Math.abs(target - strength) < .001F) strength = target;
        }
    }

    public static final class SkyEventSettings {
        public static final Codec<SkyEventSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("override", false).forGetter(settings -> settings.override),
            Codec.INT.optionalFieldOf("strength", 10).forGetter(settings -> settings.strength)
        ).apply(instance, SkyEventSettings::new));
        public volatile boolean override = false;
        public volatile int strength = 10;
        public SkyEventSettings() {}
        private SkyEventSettings(boolean override, int strength) {
            this.override = override;
            this.strength = strength;
            validate();
        }
        public void validate() { strength = Math.clamp(strength, 0, 20); }
    }

    /** Per-save overrides, with independent event settings for each dimension. */
    public static final class SkyEventWorldData extends SavedData {
        public static final SavedDataType<SkyEventWorldData> TYPE = new SavedDataType<>(
            Naturality.id("sky_events"), SkyEventWorldData::new,
            Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, SkyEventSettings.CODEC))
                .xmap(SkyEventWorldData::new, data -> data.settings),
            DataFixTypes.LEVEL);

        private final Map<String, Map<String, SkyEventSettings>> settings;

        public SkyEventWorldData() { settings = new HashMap<>(); }

        private SkyEventWorldData(Map<String, Map<String, SkyEventSettings>> saved) {
            this();
            saved.forEach((dimension, events) -> settings.put(dimension, new HashMap<>(events)));
        }

        public static SkyEventWorldData get(MinecraftServer server) {
            return server.getDataStorage().computeIfAbsent(TYPE);
        }

        public SkyEventSettings settings(ServerLevel level, SkyEventType type) {
            String dimension = level.dimension().identifier().toString();
            var events = settings.computeIfAbsent(dimension, _ -> new HashMap<>());
            var event = events.computeIfAbsent(type.id, _ -> {
                var initial = new SkyEventSettings(); initial.strength = Math.min(initial.strength, type.maxStrength()); return initial;
            });
            if (type == SkyEventType.BLOOD_MOON && event.strength > 1) {
                event.strength = 1; setDirty();
            }
            return event;
        }

        public void override(ServerLevel level, SkyEventType type, boolean enabled, int strength) {
            var event = settings(level, type);
            event.override = enabled;
            event.strength = Math.clamp(strength, 0, type.maxStrength());
            setDirty();
            java.util.Objects.requireNonNull(level.getServer()).getDataStorage().scheduleSave();
        }
    }

    public record SkyEventPayload(Identifier dimension, String event, float strength, long appearance) implements CustomPacketPayload {
        public SkyEventPayload(Identifier dimension, String event, float strength) { this(dimension, event, strength, 0); }
        public static final Type<SkyEventPayload> TYPE = new Type<>(Naturality.id("sky_event_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SkyEventPayload> CODEC = new StreamCodec<@org.jspecify.annotations.NonNull RegistryFriendlyByteBuf, @org.jspecify.annotations.NonNull SkyEventPayload>() {
            public SkyEventPayload decode(RegistryFriendlyByteBuf b) {
                return new SkyEventPayload(b.readIdentifier(), b.readUtf(64), b.readFloat(), b.readLong());
            }
            public void encode(RegistryFriendlyByteBuf b, SkyEventPayload p) {
                b.writeIdentifier(p.dimension); b.writeUtf(p.event, 64); b.writeFloat(p.strength); b.writeLong(p.appearance);
            }
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Operator command for per-dimension visual event overrides. */
    public static final class SkyEventCommand {
        private SkyEventCommand() {}
        public static void initialize() {
            CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> {
                var root = Commands.literal("skyevent")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
                root.then(Commands.literal("status").executes(context -> status(context.getSource(), context.getSource().getLevel())));
                var event = Commands.argument("event", StringArgumentType.word())
                    .suggests((context, builder) -> {
                        var dimension = DimensionArgument.getDimension(context, "dimension");
                        return SharedSuggestionProvider.suggest(SkyEventType.pool(id(dimension)).stream().map(type -> type.id), builder);
                    });
                event.then(Commands.argument("strength", IntegerArgumentType.integer(0, 20)).executes(context -> {
                    var level = DimensionArgument.getDimension(context, "dimension");
                    var type = find(level, StringArgumentType.getString(context, "event"));
                    int strength = IntegerArgumentType.getInteger(context, "strength");
                    if (strength > type.maxStrength()) throw new SimpleCommandExceptionType(
                        Component.literal(type.label + " strength must be between 0 and " + type.maxStrength())).create();
                    SkyEventWorldData.get(java.util.Objects.requireNonNull(level.getServer())).override(level, type, true, strength);
                    String visibility = "";
                    if (type == SkyEventType.PROXIMITY && strength > 0) {
                        visibility = " Planet: " + ProximityCycle.PLANETS[strength - 1] + "."
                            + (!SkyEvents.isNight(level) ? " Currently hidden during daytime." : "");
                    }
                    if (type == SkyEventType.RAINBOW && strength > 0) {
                        float rain = SkyEvents.rainfall(level);
                        if (!RainbowCycle.validRain(rain))
                            visibility += String.format(Locale.ROOT,
                                " Currently hidden: rain is %.1f/100; Rainbow needs more than 1 and less than 20.", rain);
                        if (SkyEvents.isNight(level)) visibility += " Currently hidden at night.";
                    }
                    String reply = "Sky event " + type.label + " in " + id(level)
                        + (type == SkyEventType.BLOOD_MOON ? " set " + (strength > 0 ? "on" : "off")
                            : " set to strength " + strength) + " (override)." + visibility;
                    context.getSource().sendSuccess(() -> Component.literal(reply), true);
                    return Command.SINGLE_SUCCESS;
                }));
                for (String toggle : java.util.List.of("on", "off")) {
                    event.then(Commands.literal(toggle).executes(context -> {
                        var level = DimensionArgument.getDimension(context, "dimension");
                        var type = find(level, StringArgumentType.getString(context, "event"));
                        if (type != SkyEventType.BLOOD_MOON) throw new SimpleCommandExceptionType(
                            Component.literal("Use a numeric strength for " + type.label + ".")).create();
                        SkyEventWorldData.get(java.util.Objects.requireNonNull(level.getServer())).override(level, type, true, toggle.equals("on") ? 1 : 0);
                        context.getSource().sendSuccess(() -> Component.literal("Blood Moon " + toggle + " (override)."), true);
                        return Command.SINGLE_SUCCESS;
                    }));
                }
                event.then(Commands.literal("auto").executes(context -> {
                    var level = DimensionArgument.getDimension(context, "dimension");
                    var type = find(level, StringArgumentType.getString(context, "event"));
                    var data = SkyEventWorldData.get(java.util.Objects.requireNonNull(level.getServer()));
                    data.override(level, type, false, data.settings(level, type).strength);
                    context.getSource().sendSuccess(() -> Component.literal("Sky event " + type.label + " in " + id(level)
                        + " returned to automatic scheduling."), true);
                    return Command.SINGLE_SUCCESS;
                }));
                var dimension = Commands.argument("dimension", DimensionArgument.dimension())
                    .executes(context -> status(context.getSource(), DimensionArgument.getDimension(context, "dimension")))
                    .then(Commands.literal("status").executes(context -> status(context.getSource(), DimensionArgument.getDimension(context, "dimension"))))
                    .then(event);
                dispatcher.register(root.then(dimension));
            });
        }
        private static int status(CommandSourceStack source, ServerLevel level) {
            String dimension = id(level);
            var pool = SkyEventType.pool(dimension);
            if (pool.isEmpty()) {
                source.sendSuccess(() -> Component.literal("Sky Events: " + dimension + " — none."), false);
                return Command.SINGLE_SUCCESS;
            }
            ServerPlayer observer = source.getEntity() instanceof ServerPlayer player && player.level() == level
                ? player : level.players().isEmpty() ? null : level.players().getFirst();
            var happening = new ArrayList<String>();
            var lines = new StringBuilder("Sky Events: ").append(dimension);
            boolean night = SkyEvents.isNight(level);
            for (var type : pool) {
                var settings = SkyEventWorldData.get(java.util.Objects.requireNonNull(level.getServer())).settings(level, type);
                float value = SkyEvents.strength(level, type);
                boolean active;
                boolean visible;
                String extra = "";
                String chance;
                if (type == SkyEventType.PROXIMITY) {
                    var cycle = SkyEvents.proximityCycle(level);
                    int planet = Math.clamp((int)value, 0, 7);
                    active = planet > 0;
                    visible = active && night;
                    if (active) extra += "; " + ProximityCycle.PLANETS[planet - 1];
                    if (!settings.override && active && cycle != null) extra += "; " + duration(cycle.remaining()) + " left";
                    if (!night) extra += "; hidden: day";
                    chance = percent(ProximityCycle.START_CHANCE) + "/night";
                } else if (type == SkyEventType.BLOOD_MOON) {
                    active = value > 0;
                    visible = active && night;
                    if (!night) extra += "; hidden: day";
                    else if (active && !settings.override) extra += "; until dawn";
                    chance = percent(BloodMoonCycle.START_CHANCE) + "/night";
                } else if (type == SkyEventType.METEOR_SHOWER || type == SkyEventType.END_FLASHES) {
                    var cycle = SkyEvents.cycle(level, type);
                    active = settings.override ? settings.strength > 0 : cycle != null && cycle.active();
                    visible = (active || value > .01F) && (type == SkyEventType.END_FLASHES || night);
                    if (!settings.override && active && cycle != null) extra += "; " + duration(cycle.remaining()) + " left";
                    if (type == SkyEventType.METEOR_SHOWER && !night) extra += "; hidden: day";
                    double roll = type == SkyEventType.END_FLASHES ? SkyEventCycle.END_FLASH_CHANCE_PER_TICK : SkyEventCycle.START_CHANCE_PER_TICK;
                    chance = percent(1 - Math.pow(1 - roll, 1200)) + "/min" + (type == SkyEventType.METEOR_SHOWER ? " (night)" : "");
                    if (type == SkyEventType.METEOR_SHOWER) extra += "; boost " + percent(SkyEventCycle.METEOR_BOOST_CHANCE) + "/min";
                } else if (type == SkyEventType.RAINBOW) {
                    var cycle = SkyEvents.rainbowCycle(level);
                    float rain = SkyEvents.rainfall(level);
                    active = settings.override ? value > 0 : cycle != null && cycle.active() && !night && RainbowCycle.validRain(rain);
                    visible = value > .01F;
                    extra += "; rain " + number(rain) + "/100";
                    if (night) extra += "; hidden: night";
                    if (!RainbowCycle.validRain(rain)) extra += "; needs rain 1–20";
                    if (!settings.override && active && cycle != null) extra += "; " + duration(cycle.remaining()) + " left";
                    chance = percent(1 - Math.pow(1 - RainbowCycle.startChance(rain), 1200)) + "/min (day)";
                } else {
                    var local = observer == null ? null : AuroraEvents.status(level, observer.blockPosition());
                    value = settings.override ? settings.strength : local == null ? 0 : local.strength();
                    active = settings.override ? settings.strength > 0 : local != null && local.active();
                    visible = active || value > .01F;
                    if (local == null) extra += "; no local players";
                    double roll = local == null ? 0 : Math.clamp(local.chance(), 0F, 1F);
                    chance = percent(1 - Math.pow(1 - roll, 2)) + "/min";
                }
                if (visible) happening.add(type.label);
                lines.append('\n').append(type.label).append(": ")
                    .append(type == SkyEventType.BLOOD_MOON ? (active ? "on" : "off")
                        : (active ? "active" : value > .01F ? "fading" : "quiet")
                            + " " + number(Math.round(value * 10) / 10.0) + "/" + type.maxStrength())
                    .append(settings.override ? " [override]" : "")
                    .append("; ").append(chance).append(extra);
            }
            lines.append("\nCurrent: ").append(happening.isEmpty() ? "none" : String.join(", ", happening));
            if (!level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ADVANCE_WEATHER)
                    && !level.dimension().equals(net.minecraft.world.level.Level.END))
                lines.append("\nTimers: paused (weather)");
            String message = lines.toString();
            source.sendSuccess(() -> Component.literal(message), false);
            return Command.SINGLE_SUCCESS;
        }
        private static String number(double value) {
            return new java.text.DecimalFormat("0.##", java.text.DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value);
        }
        private static String percent(double chance) {
            return chance > 0 && chance < .0001 ? "<0.01%" : number(chance * 100) + "%";
        }
        private static String duration(int ticks) {
            return ticks < 1200 ? number(ticks / 20.0) + "s" : number(ticks / 1200.0) + "m";
        }
        private static SkyEventType find(ServerLevel level, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
            String dimension = id(level);
            if (!SkyEventType.supported(dimension))
                throw new SimpleCommandExceptionType(Component.literal("Sky Events are not supported in " + dimension + ".")).create();
            return SkyEventType.pool(dimension).stream().filter(type -> type.id.equals(name)).findFirst()
                .orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("Event '" + name + "' is not available in " + dimension + ".")).create());
        }
        private static String id(ServerLevel level) { return level.dimension().identifier().toString(); }
    }
}
