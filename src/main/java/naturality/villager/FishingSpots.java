package naturality.villager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import naturality.util.LoadedChunks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Shared, bounded, server-thread-only shoreline surveys. Never loads chunks. */
public final class FishingSpots {
    public record Spot(BlockPos water, BlockPos stand) { }
    private static final int RADIUS = 16, WIDTH = 33, CELLS = WIDTH * WIDTH * 8;
    private static final int CELLS_PER_TICK = 512, MAX_SURVEYS = 64;
    private static final Map<ServerLevel, Cache> LEVELS = new WeakHashMap<>();

    private FishingSpots() { }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerLevel level : server.getAllLevels()) tick(level);
        });
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, _) -> chunkChanged(level, chunk.getPos()));
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> chunkChanged(level, chunk.getPos()));
        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> LEVELS.clear());
    }

    /** Nearby job sites share even unfinished/empty surveys; paths remain per villager. */
    public static List<Spot> nearby(ServerLevel level, BlockPos origin) {
        Cache cache = LEVELS.computeIfAbsent(level, _ -> new Cache());
        long now = level.getGameTime();
        Survey found = null;
        for (Survey survey : cache.surveys) {
            if (Math.abs(origin.getX() - survey.origin.getX()) <= 4
                    && Math.abs(origin.getZ() - survey.origin.getZ()) <= 4
                    && origin.getY() == survey.origin.getY()) { found = survey; break; }
        }
        if (found == null) {
            if (cache.surveys.size() >= MAX_SURVEYS) {
                Survey oldest = Collections.min(cache.surveys, Comparator.comparingLong(s -> s.lastUsed));
                cache.remove(oldest);
            }
            found = new Survey(origin.immutable());
            cache.surveys.add(found);
            for (int x = (origin.getX() - 18) >> 4; x <= (origin.getX() + 18) >> 4; x++)
                for (int z = (origin.getZ() - 18) >> 4; z <= (origin.getZ() + 18) >> 4; z++)
                    cache.byChunk.computeIfAbsent(ChunkPos.pack(x, z), _ -> new ArrayList<>()).add(found);
        }
        found.lastUsed = now;
        found.requested = found.cursor != CELLS;
        return found.result;
    }

    public static boolean usable(ServerLevel level, Spot spot) {
        return standable(level, spot.stand) && ProfessionWork.restingWater(level, spot.water);
    }

    private static boolean standable(ServerLevel level, BlockPos stand) {
        if (!LoadedChunks.has(level, stand) || !level.getBlockState(stand).isAir()
                || !level.getBlockState(stand.above()).isAir()) return false;
        BlockPos below = stand.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    public static void blockChanged(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        Cache cache = LEVELS.get(level);
        if (cache == null) return;
        List<Survey> surveys = cache.byChunk.get(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
        if (surveys == null) return;
        for (Survey survey : surveys) {
            if (Math.abs(pos.getX() - survey.origin.getX()) <= 18
                    && Math.abs(pos.getZ() - survey.origin.getZ()) <= 18
                    && pos.getY() >= survey.origin.getY() - 5 && pos.getY() <= survey.origin.getY() + 6
                    && (before.is(Blocks.WATER) || after.is(Blocks.WATER) || survey.nearWater(pos)))
                survey.invalidate();
        }
    }

    private static void chunkChanged(ServerLevel level, ChunkPos pos) {
        Cache cache = LEVELS.get(level);
        if (cache == null) return;
        List<Survey> surveys = cache.byChunk.get(pos.pack());
        if (surveys != null) for (Survey survey : surveys) survey.invalidate();
    }

    private static void tick(ServerLevel level) {
        Cache cache = LEVELS.get(level);
        if (cache == null) return;
        // Round-robin prevents a group in one village from multiplying the budget.
        int remaining = CELLS_PER_TICK;
        for (int visited = 0; visited < cache.surveys.size() && remaining > 0; visited++) {
            cache.next = (cache.next + 1) % cache.surveys.size();
            Survey survey = cache.surveys.get(cache.next);
            if (level.getGameTime() - survey.lastUsed > 12000 || !survey.requested || survey.cursor == CELLS) continue;
            remaining -= survey.advance(level, remaining);
        }
    }

    private static final class Cache {
        final List<Survey> surveys = new ArrayList<>();
        final Map<Long, List<Survey>> byChunk = new HashMap<>();
        int next;
        void remove(Survey survey) {
            surveys.remove(survey);
            byChunk.values().removeIf(list -> { list.remove(survey); return list.isEmpty(); });
        }
    }

    private static final class Survey {
        final BlockPos origin;
        final Set<BlockPos> water = new HashSet<>(), sources = new HashSet<>();
        List<Spot> result = List.of();
        int cursor;
        long lastUsed;
        boolean requested;
        Survey(BlockPos origin) { this.origin = origin; }
        void invalidate() { cursor = 0; water.clear(); sources.clear(); result = List.of(); }

        boolean nearWater(BlockPos changed) {
            var probe = new BlockPos.MutableBlockPos();
            for (int y = -3; y <= 3; y++) for (int z = -2; z <= 2; z++) for (int x = -2; x <= 2; x++) {
                probe.set(changed.getX() + x, changed.getY() + y, changed.getZ() + z);
                if (sources.contains(probe)) return true;
            }
            return false;
        }

        int advance(ServerLevel level, int budget) {
            int end = Math.min(CELLS, cursor + budget), used = end - cursor;
            var pos = new BlockPos.MutableBlockPos();
            while (cursor < end) {
                int i = cursor++;
                pos.set(origin.getX() + i % WIDTH - RADIUS, origin.getY() + i / (WIDTH * WIDTH) - 4,
                    origin.getZ() + i / WIDTH % WIDTH - RADIUS);
                if (LoadedChunks.has(level, pos) && level.getBlockState(pos).is(Blocks.WATER)) {
                    sources.add(pos.immutable());
                    if (ProfessionWork.restingWater(level, pos)) water.add(pos.immutable());
                }
            }
            if (cursor == CELLS) { result = finish(level); water.clear(); requested = false; }
            return used;
        }

        private List<Spot> finish(ServerLevel level) {
            List<Spot> candidates = new ArrayList<>();
            Set<BlockPos> unseen = new HashSet<>(water);
            var probe = new BlockPos.MutableBlockPos();
            while (!unseen.isEmpty()) {
                Set<BlockPos> pool = new HashSet<>();
                ArrayDeque<BlockPos> queue = new ArrayDeque<>();
                BlockPos seed = unseen.iterator().next();
                unseen.remove(seed);
                queue.add(seed);
                double sumX = 0, sumZ = 0;
                while (!queue.isEmpty()) {
                    BlockPos current = queue.removeFirst();
                    pool.add(current); sumX += current.getX(); sumZ += current.getZ();
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        BlockPos next = current.relative(dir);
                        if (unseen.remove(next)) queue.addLast(next);
                    }
                }
                double centerX = sumX / pool.size(), centerZ = sumZ / pool.size();
                Map<BlockPos, Double> scores = new HashMap<>();
                Set<BlockPos> stands = new HashSet<>();
                for (BlockPos p : pool) {
                    int interior = 0;
                    for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                        probe.set(p.getX() + dx, p.getY(), p.getZ() + dz);
                        if (water.contains(probe)) interior++;
                    }
                    double dx = p.getX() - centerX, dz = p.getZ() - centerZ;
                    scores.put(p, interior * 4.0 - (dx * dx + dz * dz) * .15);
                    for (Direction dir : Direction.Plane.HORIZONTAL) for (int dy = 0; dy <= 1; dy++) {
                        BlockPos stand = p.relative(dir).above(dy);
                        // Deduplicate before querying the world, and skip known water.
                        if (!water.contains(stand)) stands.add(stand);
                    }
                }
                for (BlockPos stand : stands) {
                    if (!standable(level, stand)) continue;
                    BlockPos cast = null;
                    double best = -Double.MAX_VALUE;
                    // Horizontal components have one Y. Only inspect the cast's ten-block radius.
                    for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) {
                        int dy = stand.getY() - seed.getY(), distance = dx * dx + dy * dy + dz * dz;
                        if (distance > 100) continue;
                        probe.set(stand.getX() + dx, seed.getY(), stand.getZ() + dz);
                        Double base = scores.get(probe);
                        if (base != null && base - distance * .08 > best) {
                            best = base - distance * .08;
                            cast = probe.immutable();
                        }
                    }
                    if (cast != null) candidates.add(new Spot(cast, stand));
                }
            }
            return List.copyOf(candidates);
        }
    }
}
