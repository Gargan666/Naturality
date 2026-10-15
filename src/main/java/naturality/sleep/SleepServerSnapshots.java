package naturality.sleep;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.clock.ClockState;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.level.gamerules.GameRules;

/** Constant work per world clock, with at most ten snapshot broadcasts per second. */
public final class SleepServerSnapshots {
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
