package naturality.systems.sleep;

import java.util.Map;
import java.util.WeakHashMap;
import naturality.config.gameplay.GameplaySettings.NaturalityServerConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.clock.ClockState;
import net.minecraft.world.clock.ServerClockManager;
import naturality.Naturality;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Advance celestial time without multiplying world simulation work. */
public final class SleepAcceleration {
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
    private static final Map<ServerPlayer, Rest> REST = new WeakHashMap<>();
    private static final class State { double seconds, speed = 1, clockTicks = 1; }
    private static final class Rest { double ticks; }
    private SleepAcceleration() {}
    public static boolean active(MinecraftServer server) { return STATES.containsKey(server); }
    public static double speed(MinecraftServer server) {
        var state = STATES.get(server);
        return state == null ? 1 : state.speed;
    }
    public static double clockTicks(MinecraftServer server) {
        var state = STATES.get(server);
        return state == null ? 1 : state.clockTicks;
    }
    public static boolean enabled() { return NaturalityServerConfig.get().sleepAcceleration; }
    public static void started(ServerPlayer player) {
        if (!enabled()) return;
        REST.put(player, new Rest());
    }
    public static void stopped(ServerPlayer player) {
        REST.remove(player);
        var server = player.level().getServer();
        // Stop in the wake operation itself, before another world clock update.
        if (!enoughSleeping(server)) restore(server);
    }
    private static boolean enoughSleeping(MinecraftServer server) {
        if (!enabled() || server.tickRateManager().isFrozen() || server.tickRateManager().isSprinting()) return false;
        for (var level : server.getAllLevels()) {
            int total = 0, sleeping = 0;
            for (var player : level.players()) {
                if (player.isSpectator()) continue;
                total++;
                if (player.isSleeping()) sleeping++;
            }
            int percentage = level.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
            if (percentage <= 100 && sleeping > 0 && sleeping >= Math.max(1, (int)Math.ceil(total * percentage / 100.0))) return true;
        }
        return false;
    }
    private static void restore(MinecraftServer server) {
        var state = STATES.remove(server);
        if (state == null) return;
        SleepServerSnapshots.finish(server);
        sendState(server);
    }
    public static double multiplier(double seconds, int maximum, int rampSeconds) {
        return 1 + (maximum - 1) * Math.clamp(seconds / rampSeconds, 0, 1);
    }
    /** Integral of the linear ramp, including intervals crossing its cap. */
    public static double elapsedTicks(double seconds, double duration, int maximum, int rampSeconds, double tickRate) {
        if (seconds >= rampSeconds) return duration * maximum * tickRate;
        double rampStart = Math.min(seconds, rampSeconds), rampEnd = Math.min(seconds + duration, rampSeconds);
        double rampArea = (rampEnd * rampEnd - rampStart * rampStart) / (2 * rampSeconds);
        double cappedArea = Math.max(0, seconds + duration - Math.max(seconds, rampSeconds));
        return tickRate * (duration + (maximum - 1) * (rampArea + cappedArea));
    }
    public static void initialize() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(SleepStatePayload.TYPE, SleepStatePayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, _, server) -> {
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(handler.player, SleepStatePayload.TYPE))
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(handler.player, new SleepStatePayload(active(server), speed(server)));
        });
        ServerTickEvents.START_SERVER_TICK.register(SleepAcceleration::tick);
        ServerTickEvents.END_SERVER_TICK.register(SleepServerSnapshots::synchronize);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { STATES.remove(server); REST.clear(); SleepServerSnapshots.clear(server); });
    }
    public static void tick(MinecraftServer server) {
        var manager = server.tickRateManager();
        var config = NaturalityServerConfig.get();
        var state = STATES.get(server);
        boolean eligible = false;
        if (enabled() && !manager.isFrozen() && !manager.isSprinting()) {
            for (var level : server.getAllLevels()) {
                int total = 0, sleeping = 0;
                for (var player : level.players()) {
                    if (player.isSpectator()) continue;
                    total++;
                    if (!player.isSleeping()) { REST.remove(player); continue; }
                    if (!REST.containsKey(player)) started(player);
                    sleeping++;
                }
                int percentage = level.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
                if (percentage <= 100 && sleeping > 0 && sleeping >= Math.max(1, (int)Math.ceil(total * percentage / 100.0))) eligible = true;
            }
        }
        if (!eligible) {
            restore(server);
            if (!enabled()) REST.clear();
            else if (!manager.isFrozen() && !manager.isSprinting()) rest(server, 1);
            return;
        }
        if (state == null) {
            state = new State();
            STATES.put(server, state);
        }
        double duration = 1.0 / manager.tickrate();
        int maximum = Math.clamp(config.sleepMaxSpeed, 1, 1000), ramp = Math.clamp(config.sleepRampSeconds, 1, 120);
        state.clockTicks = elapsedTicks(state.seconds, duration, maximum, ramp, manager.tickrate());
        state.seconds += duration;
        state.speed = multiplier(state.seconds, maximum, ramp);
        rest(server, state.clockTicks);
    }
    private static void rest(MinecraftServer server, double ticks) {
        for (var entry : REST.entrySet()) {
            var player = entry.getKey();
            if (player.level().getServer() != server || !player.isSleeping()) continue;
            var rest = entry.getValue();
            rest.ticks = Math.min(6001, rest.ticks + ticks);
            if (rest.ticks > 6000 && player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)) != 0)
                player.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        }
    }
    static void sendState(MinecraftServer server) {
        for (var player : server.getPlayerList().getPlayers())
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, SleepStatePayload.TYPE))
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new SleepStatePayload(active(server), speed(server)));
    }

    /** Constant work per world clock, with at most ten snapshot broadcasts per second. */
    public static final class SleepServerSnapshots {
        private static final Map<MinecraftServer, Session> SESSIONS = new WeakHashMap<>();
        private static final class Session {
            final ServerClockManager.ServerClockInstance[] clocks;
            long packetNanos;
            Session(MinecraftServer server) {
                clocks = server.clockManager().packState().clocks().keySet().stream()
                    .map(server.clockManager()::getInstance).toArray(ServerClockManager.ServerClockInstance[]::new);
            }
        }
        private SleepServerSnapshots() {}
        @SuppressWarnings("deprecation")
        public static void tickClocks(MinecraftServer server) {
            if (!SleepAcceleration.active(server)) { server.clockManager().tick(); return; }
            if (!server.getGlobalGameRules().get(GameRules.ADVANCE_TIME)) return;
            var session = SESSIONS.computeIfAbsent(server, Session::new);
            double ticks = SleepAcceleration.clockTicks(server);
            for (var clock : session.clocks) {
                if (clock.isPaused()) continue;
                double advance = clock.partialTick() + ticks * clock.rate();
                long whole = (long)Math.floor(advance);
                clock.loadFrom(new ClockState(clock.totalTicks() + whole,
                    (float)(advance - whole), clock.rate(), false));
            }
            server.clockManager().setDirty();
            for (var level : server.getAllLevels()) level.environmentAttributes().invalidateTickCache();
        }
        public static void synchronize(MinecraftServer server) {
            if (!SleepAcceleration.active(server)) return;
            var session = SESSIONS.computeIfAbsent(server, Session::new);
            long now = System.nanoTime();
            if (now - session.packetNanos < 100_000_000L) return;
            session.packetNanos = now;
            server.getPlayerList().broadcastAll(server.clockManager().createFullSyncPacket());
            SleepAcceleration.sendState(server);
        }
        public static void finish(MinecraftServer server) {
            clear(server);
            server.getPlayerList().broadcastAll(server.clockManager().createFullSyncPacket());
        }
        public static void clear(MinecraftServer server) { SESSIONS.remove(server); }
    }

    /** Visual speed is independent of the server's simulation tick rate. */
    public record SleepStatePayload(boolean accelerating, double speed) implements CustomPacketPayload {
        public static final Type<SleepStatePayload> TYPE = new Type<>(Naturality.id("sleep_state_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SleepStatePayload> CODEC = new StreamCodec<>() {
            public SleepStatePayload decode(RegistryFriendlyByteBuf buffer) { return new SleepStatePayload(buffer.readBoolean(), buffer.readDouble()); }
            public void encode(RegistryFriendlyByteBuf buffer, SleepStatePayload payload) { buffer.writeBoolean(payload.accelerating); buffer.writeDouble(payload.speed); }
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
