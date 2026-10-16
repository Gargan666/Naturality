package naturality.systems.event.framework;

import java.util.*;
import java.util.function.LongFunction;
import naturality.Naturality;
import naturality.api.worlds.WorldEventOverrides;
import naturality.api.worlds.EnvironmentWorldData;
import naturality.api.worlds.PreviewEnvironment;
import naturality.config.startup.FeatureConfig.Feature;
import naturality.systems.event.overworld.bloodmoon.BloodMoonCycle;
import naturality.systems.skyevents.framework.SkyEvents.SkyEventWorldData;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedDataType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

/** Concurrent gameplay events. Each registered item owns its eligibility and behavior; visuals are optional. */
@org.jspecify.annotations.NullMarked
public final class Events {
    private static final Map<ServerLevel, Map<EventType, Cycle>> STATES = new WeakHashMap<>();
    private Events() {}

    public interface Cycle {
        void tick(ServerLevel level, int startMultiplier);
        int strength();
        Map<String, Long> snapshot();
        void restore(Map<String, Long> saved);
    }

    public enum EventType {
        BLOOD_MOON("blood_moon", "Blood Moon", "minecraft:overworld", BloodMoonCycle::new);
        public final String id, label;
        private final String dimension;
        private final LongFunction<Cycle> factory;
        EventType(String id, String label, String dimension, LongFunction<Cycle> factory) {
            this.id = id; this.label = label; this.dimension = dimension; this.factory = factory;
        }
        public int maxStrength() { return 1; }
        public static List<EventType> pool(String dimension) {
            return Arrays.stream(values()).filter(type -> type.dimension.equals(dimension)).toList();
        }
    }

    public static float strength(ServerLevel level, EventType type) {
        if (!Feature.EVENT_CYCLES.enabled() || !EventType.pool(id(level)).contains(type)) return 0;
        var settings = EventWorldData.get(Objects.requireNonNull(level.getServer())).settings(level, type);
        if (settings.override) return settings.strength;
        var cycles = STATES.get(level);
        var cycle = cycles == null ? null : cycles.get(type);
        return cycle == null ? 0 : cycle.strength();
    }

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(EventPayload.TYPE, EventPayload.CODEC);
        if (!Feature.EVENT_CYCLES.enabled()) return;
        initializeCommands();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            for (var level : server.getAllLevels()) cycles(level);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> STATES.clear());
        ServerPlayConnectionEvents.JOIN.register((handler, _, server) -> {
            for (var level : server.getAllLevels()) send(handler.player, level);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var level : server.getAllLevels()) {
                if (!PreviewEnvironment.frozen(server)) {
                    cycles(level).forEach((type, cycle) -> {
                        cycle.tick(level, PreviewEnvironment.rate(server));
                        EnvironmentWorldData.get(server).write(key(level, type), cycle.snapshot());
                    });
                }
                if (server.getTickCount() % 10 == 0)
                    for (var player : server.getPlayerList().getPlayers()) send(player, level);
            }
        });
    }

    private static Map<EventType, Cycle> cycles(ServerLevel level) {
        return STATES.computeIfAbsent(level, world -> {
            var cycles = new EnumMap<EventType, Cycle>(EventType.class);
            var server = Objects.requireNonNull(world.getServer());
            var data = EnvironmentWorldData.get(server);
            for (var type : EventType.pool(id(world))) {
                var cycle = type.factory.apply(world.getSeed() ^ type.id.hashCode());
                var saved = data.read(key(world, type));
                // Blood Moon previously belonged to sky events; consume that snapshot only once.
                String legacyKey = "sky/" + id(world) + "/" + type.id;
                if (saved.isEmpty()) saved = data.read(legacyKey);
                cycle.restore(saved);
                data.write(key(world, type), cycle.snapshot());
                data.remove(legacyKey);
                EventWorldData.get(server).settings(world, type);
                cycles.put(type, cycle);
            }
            return cycles;
        });
    }
    private static String id(ServerLevel level) { return level.dimension().identifier().toString(); }
    private static String key(ServerLevel level, EventType type) { return "event/" + id(level) + "/" + type.id; }
    private static void send(ServerPlayer player, ServerLevel level) {
        if (!ServerPlayNetworking.canSend(player, EventPayload.TYPE)) return;
        for (var type : EventType.pool(id(level)))
            ServerPlayNetworking.send(player, new EventPayload(level.dimension().identifier(), type.id, strength(level, type)));
    }

    /** Independent saved-data owner sharing the event override serialization contract. */
    public static final class EventWorldData extends WorldEventOverrides {
        public static final SavedDataType<EventWorldData> TYPE = new SavedDataType<>(
            Naturality.id("events"), EventWorldData::new,
            WorldEventOverrides.codec(1).xmap(EventWorldData::new, data -> data.entries()), DataFixTypes.LEVEL);
        public EventWorldData() { super(Map.of()); }
        private EventWorldData(Map<String, Map<String, Settings>> saved) { super(saved); }
        public static EventWorldData get(MinecraftServer server) {
            return server.getDataStorage().computeIfAbsent(TYPE);
        }
        @Override protected Settings initial(ServerLevel level, String event, int defaultStrength) {
            var legacy = SkyEventWorldData.get(Objects.requireNonNull(level.getServer()))
                .takeLegacyEvent(level.dimension().identifier().toString(), event);
            return legacy == null ? super.initial(level, event, defaultStrength) : legacy;
        }
        public Settings settings(ServerLevel level, EventType type) {
            return settings(level, type.id, Math.min(1, type.maxStrength()), type.maxStrength());
        }
        public void override(ServerLevel level, EventType type, boolean enabled, int strength) {
            override(level, type.id, enabled, strength, type.maxStrength());
        }
    }

    public record EventPayload(Identifier dimension, String event, float strength) implements CustomPacketPayload {
        public static final Type<EventPayload> TYPE = new Type<>(Naturality.id("event"));
        public static final StreamCodec<RegistryFriendlyByteBuf, EventPayload> CODEC = new StreamCodec<@org.jspecify.annotations.NonNull RegistryFriendlyByteBuf, @org.jspecify.annotations.NonNull EventPayload>() {
            public EventPayload decode(RegistryFriendlyByteBuf buffer) {
                return new EventPayload(buffer.readIdentifier(), buffer.readUtf(64), buffer.readFloat());
            }
            public void encode(RegistryFriendlyByteBuf buffer, EventPayload payload) {
                buffer.writeIdentifier(payload.dimension); buffer.writeUtf(payload.event, 64); buffer.writeFloat(payload.strength);
            }
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void initializeCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> {
            var root = Commands.literal("event").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
            root.then(Commands.literal("status").executes(context -> status(context.getSource(), context.getSource().getLevel())));
            var event = Commands.argument("event", StringArgumentType.word()).suggests((context, builder) ->
                SharedSuggestionProvider.suggest(EventType.pool(id(DimensionArgument.getDimension(context, "dimension")))
                    .stream().map(type -> type.id), builder));
            for (String mode : List.of("on", "off", "auto"))
                event.then(Commands.literal(mode).executes(context -> override(context, mode, mode.equals("on") ? 1 : 0)));
            event.then(Commands.argument("strength", IntegerArgumentType.integer(0, 1))
                .executes(context -> override(context, "override", IntegerArgumentType.getInteger(context, "strength"))));
            var dimension = Commands.argument("dimension", DimensionArgument.dimension())
                .executes(context -> status(context.getSource(), DimensionArgument.getDimension(context, "dimension")))
                .then(Commands.literal("status").executes(context -> status(context.getSource(), DimensionArgument.getDimension(context, "dimension"))))
                .then(event);
            dispatcher.register(root.then(dimension));
        });
    }
    private static int override(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context,
            String mode, int strength) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var level = DimensionArgument.getDimension(context, "dimension");
        String name = StringArgumentType.getString(context, "event");
        var type = EventType.pool(id(level)).stream().filter(item -> item.id.equals(name)).findFirst()
            .orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("Event '" + name + "' is not available in " + id(level))).create());
        EventWorldData.get(Objects.requireNonNull(level.getServer())).override(level, type, !mode.equals("auto"), strength);
        for (var player : Objects.requireNonNull(level.getServer()).getPlayerList().getPlayers()) send(player, level);
        context.getSource().sendSuccess(() -> Component.literal(type.label + " in " + id(level) + ": "
            + (mode.equals("auto") ? "automatic scheduling" : strength > 0 ? "on (override)" : "off (override)")), true);
        return 1;
    }
    private static int status(net.minecraft.commands.CommandSourceStack source, ServerLevel level) {
        var message = new StringBuilder("Events: ").append(id(level));
        var pool = EventType.pool(id(level));
        if (pool.isEmpty()) message.append(" - none");
        for (var type : pool) {
            var settings = EventWorldData.get(Objects.requireNonNull(level.getServer())).settings(level, type);
            message.append('\n').append(type.label).append(": ").append(strength(level, type) > 0 ? "on" : "off")
                .append(settings.override ? " [override]" : " [automatic]");
        }
        source.sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }
}
