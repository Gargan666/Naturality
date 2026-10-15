package naturality.api.worlds;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/** Explicit, scoped no-save policy shared by previews and test worlds. Normal saves are never inferred from names. */
@org.jspecify.annotations.NullMarked
public final class DisposableWorlds {
    private static final ConcurrentHashMap<Path, Integer> ROOTS = new ConcurrentHashMap<>();

    /** Register before opening storage; retain the scope until the server and its storage workers have closed. */
    public static Scope open(Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        if (normalized.getParent() == null) throw new IllegalArgumentException("A filesystem root cannot be a disposable world");
        ROOTS.merge(normalized, 1, Integer::sum);
        return new Scope(normalized);
    }

    public static boolean discards(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return ROOTS.keySet().stream().anyMatch(normalized::startsWith);
    }

    public static boolean discards(@Nullable MinecraftServer server) {
        return server != null && discards(server.getWorldPath(LevelResource.ROOT));
    }

    /** Discarded chunks/entities are not restored after unloading; persistence checks must use a real save. */
    public static final class Scope implements AutoCloseable {
        private final Path root;
        private boolean closed;
        private Scope(Path root) { this.root = root; }
        public Path root() { return root; }
        @Override public synchronized void close() {
            if (closed) return;
            ROOTS.computeIfPresent(root, (_, count) -> count == 1 ? null : count - 1);
            closed = true;
        }
    }

    private DisposableWorlds() {}
}
