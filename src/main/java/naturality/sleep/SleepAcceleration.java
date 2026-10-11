package naturality.sleep;

import java.util.Map;
import java.util.WeakHashMap;
import naturality.config.NaturalityServerConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.gamerules.GameRules;

/** Real simulation ticks, synchronized by vanilla's tick-rate protocol. */
public final class SleepAcceleration {
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
    private static final Map<ServerPlayer, Rest> REST = new WeakHashMap<>();
    private static final class State { float baseline, applied; double seconds; }
    private static final class Rest { int ticks; }
    private SleepAcceleration() {}
    public static boolean enabled() { return NaturalityServerConfig.get().sleepAcceleration; }
    public static void started(ServerPlayer player) {
        if (!enabled()) return;
        REST.put(player, new Rest());
    }
    public static void stopped(ServerPlayer player) {
        REST.remove(player);
        var server = player.level().getServer();
        // Wake packets run outside END_SERVER_TICK too. Restore in the wake
        // operation itself, without waiting for another accelerated tick.
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
        var manager = server.tickRateManager();
        if (Math.abs(manager.tickrate() - state.applied) < .01f) manager.setTickRate(state.baseline);
        sendState(server, false);
    }
    public static double multiplier(double seconds, int maximum, int rampSeconds) {
        return 1 + (maximum - 1) * Math.clamp(seconds / rampSeconds, 0, 1);
    }
    public static void initialize() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(SleepStatePayload.TYPE, SleepStatePayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, _, server) -> {
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(handler.player, SleepStatePayload.TYPE))
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(handler.player, new SleepStatePayload(STATES.containsKey(server)));
        });
        ServerTickEvents.END_SERVER_TICK.register(SleepAcceleration::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { STATES.remove(server); REST.clear(); });
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
                    var rest = REST.get(player);
                    if (rest == null) { started(player); rest = REST.get(player); }
                    if (rest.ticks < 6001) rest.ticks++;
                    // Half of a 12,000-tick night must actually elapse asleep.
                    if (rest.ticks == 6001 && player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)) != 0)
                        player.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
                    sleeping++;
                }
                int percentage = level.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
                if (percentage <= 100 && sleeping > 0 && sleeping >= Math.max(1, (int)Math.ceil(total * percentage / 100.0))) eligible = true;
            }
        }
        if (!eligible) {
            restore(server);
            if (!enabled()) REST.clear();
            return;
        }
        if (state == null) {
            state = new State(); state.baseline = manager.tickrate(); state.applied = state.baseline;
            STATES.put(server, state);
            sendState(server, true);
        } else if (Math.abs(manager.tickrate() - state.applied) > .01f) {
            // An administrator's /tick rate change becomes the new baseline.
            state.baseline = manager.tickrate(); state.seconds = 0;
        }
        state.seconds += 1.0 / manager.tickrate();
        float rate = (float)Math.min(10000, state.baseline * multiplier(state.seconds, Math.clamp(config.sleepMaxSpeed, 1, 1000), Math.clamp(config.sleepRampSeconds, 1, 120)));
        if (Math.abs(rate - state.applied) >= .1f || (state.seconds >= config.sleepRampSeconds && rate != state.applied)) {
            manager.setTickRate(rate); state.applied = rate;
        }
    }
    private static void sendState(MinecraftServer server, boolean active) {
        for (var player : server.getPlayerList().getPlayers())
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, SleepStatePayload.TYPE))
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new SleepStatePayload(active));
    }
}
