package naturality.api.worlds;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
/** Explicit disposable-preview registration, safe on dedicated servers. */
@org.jspecify.annotations.NullMarked
public final class PreviewEnvironment {
 private static final Map<MinecraftServer, Boolean> SERVERS = new WeakHashMap<>();
 public static synchronized void register(MinecraftServer server, boolean frozen) { SERVERS.put(server, frozen); }
 public static synchronized boolean preview(@org.jspecify.annotations.Nullable MinecraftServer server) { return server != null && SERVERS.containsKey(server); }
 public static synchronized boolean frozen(MinecraftServer server) {
     return Boolean.TRUE.equals(SERVERS.get(server)) || preview(server) && server.tickRateManager().isFrozen();
 }
 public static int rate(MinecraftServer server) { return preview(server) ? 4 : 1; }
 public static double chance(double base, int multiplier) { return Math.clamp(base * multiplier, 0, 1); }
 private PreviewEnvironment() { }
}
