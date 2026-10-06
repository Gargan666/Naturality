package naturality.snow;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import naturality.fire.FireSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Bounded, thread-confined memoization, valid only during one mesh build/emission. */
public final class SnowGeometryCache {
    private static final ThreadLocal<@Nullable Scope> CURRENT = new ThreadLocal<>();
    private SnowGeometryCache() {}
    private static final class Scope {
        int depth = 1;
        @Nullable BlockGetter view;
        final Long2ObjectOpenHashMap<Entry> entries = new Long2ObjectOpenHashMap<>();
    }
    static final class Entry {
        @Nullable Boolean vanilla;
        @Nullable List<FireSurface.Patch> surfaces;
        @Nullable VoxelShape @Nullable [] shapes;
    }
    public static void begin() {
        Scope scope = CURRENT.get();
        if (scope == null) CURRENT.set(new Scope()); else scope.depth++;
    }
    public static void end() {
        Scope scope = CURRENT.get();
        if (scope != null && --scope.depth == 0) CURRENT.remove();
    }
    static @Nullable Entry entry(BlockGetter view, BlockPos pos) {
        Scope scope = CURRENT.get();
        if (scope == null || ShapeRecursionGuard.fireActive()) return null;
        if (scope.view == null) scope.view = view;
        if (scope.view != view) return null;
        long key = pos.asLong();
        Entry entry = scope.entries.get(key);
        if (entry == null) {
            if (scope.entries.size() >= 8192) scope.entries.clear();
            entry = new Entry(); scope.entries.put(key, entry);
        }
        return entry;
    }
}
