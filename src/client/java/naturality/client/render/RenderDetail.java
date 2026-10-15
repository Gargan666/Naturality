package naturality.client.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import naturality.config.NaturalityConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Section-sized LODs with hysteresis and bounded remesh requests. No world scans. */
public final class RenderDetail {
    private static final Map<Long, Detail> SECTIONS = new ConcurrentHashMap<>();
    private static volatile @Nullable ClientLevel world;
    private static volatile Vec3 camera = Vec3.ZERO;
    private static volatile long epoch;
    private static final ThreadLocal<Cursor> CURSOR = ThreadLocal.withInitial(Cursor::new);
    private static final class Cursor {
        long key, epoch = -1; Detail detail; int builds;
        final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap fixed = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    }
    public static void beginBuild() { CURSOR.get().builds++; }
    public static void endBuild() { var cursor = CURSOR.get(); if (--cursor.builds == 0) cursor.fixed.clear(); }
    private static int remember(Cursor cursor, long key, int tier) {
        if (cursor.builds > 0) cursor.fixed.put(key, tier);
        return tier;
    }
    private static int tick;
    private static int settings;
    private static @Nullable Vec3 lastScan;
    private static boolean pendingTransitions;
    public static boolean settled() { return !pendingTransitions; }
    private static final class Detail { volatile int tier; boolean pending; Detail(int tier) { this.tier = tier; } }
    private RenderDetail() {}
    public static void initialize() { ClientTickEvents.END_CLIENT_TICK.register(RenderDetail::tick); }
    public static void view(@Nullable ClientLevel level, Vec3 position) {
        if (world != level) {
            SECTIONS.clear(); world = level; epoch++;
            lastScan = null; pendingTransitions = false;
            var effects = NaturalityConfig.get().effects;
            settings = (effects.distantLeaves ? 1 : 0) | (effects.distantTextures ? 2 : 0) | (effects.distantDetails ? 8 : 0);
        }
        camera = position;
    }
    public static int tier(double distance, int previous) {
        var effects = NaturalityConfig.get().effects;
        if (effects.distantDetails && distance >= (previous >= 2 ? 160 : 192)) return 2;
        if (effects.distantLeaves && distance >= (previous >= 1 ? 128 : 160)) return 1;
        return 0;
    }
    public static boolean reducedDetails(BlockPos pos) {
        return NaturalityConfig.get().effects.distantDetails && at(pos) >= 2;
    }
    private static int sectionTier(long section, int previous) {
        return tier(distance(section), previous);
    }
    private static double distance(long section) {
        Vec3 p = camera;
        double x = SectionPos.x(section) * 16.0, y = SectionPos.y(section) * 16.0, z = SectionPos.z(section) * 16.0;
        double dx = Math.max(Math.max(x - p.x, p.x - x - 16), 0);
        double dy = Math.max(Math.max(y - p.y, p.y - y - 16), 0);
        double dz = Math.max(Math.max(z - p.z, p.z - z - 16), 0);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    public static int at(BlockPos pos) {
        if (world == null) return 0;
        long key = SectionPos.asLong(pos);
        var cursor = CURSOR.get();
        long currentEpoch = epoch;
        if (cursor.builds > 0) {
            int fixed = cursor.fixed.getOrDefault(key, -1);
            if (fixed >= 0) return fixed;
        }
        if (cursor.epoch == currentEpoch && cursor.key == key) return remember(cursor, key, cursor.detail.tier);
        var existing = SECTIONS.get(key);
        // Unknown overflow falls back to full detail, never an untracked coarse mesh.
        if (existing == null) {
            if (SECTIONS.size() >= 32768) return 0;
            existing = SECTIONS.computeIfAbsent(key, k -> new Detail(sectionTier(k, 0)));
        }
        cursor.key = key; cursor.epoch = currentEpoch; cursor.detail = existing;
        return remember(cursor, key, existing.tier);
    }
    private static void tick(Minecraft client) {
        view(client.level, client.gameRenderer.mainCamera().position());
        var level = world;
        if (level == null || ++tick % 4 != 0) return;
        var effects = NaturalityConfig.get().effects;
        int flags = (effects.distantLeaves ? 1 : 0) | (effects.distantTextures ? 2 : 0) | (effects.distantDetails ? 8 : 0);
        if (!pendingTransitions && flags == settings && lastScan != null
                && camera.distanceToSqr(lastScan) < 1 && tick % 100 != 0) return;
        lastScan = camera;
        pendingTransitions = false;
        if (settings != flags) {
            settings = flags;
            SECTIONS.values().forEach(detail -> detail.pending = true);
        }
        int coarseBudget = 8, nearBudget = 32;
        boolean pruned = false;
        for (var entry : SECTIONS.entrySet()) {
            long key = entry.getKey();
            int x = SectionPos.x(key), y = SectionPos.y(key), z = SectionPos.z(key);
            if (!level.hasChunk(x, z)) { SECTIONS.remove(key, entry.getValue()); pruned = true; continue; }
            var detail = entry.getValue();
            int next = sectionTier(key, detail.tier);
            if (next == detail.tier && !detail.pending) continue;
            boolean towardCamera = next < detail.tier;
            if (towardCamera ? nearBudget <= 0 : coarseBudget <= 0) { pendingTransitions = true; continue; }
            if (towardCamera) nearBudget--; else coarseBudget--;
            detail.tier = next;
            detail.pending = false;
            // Neighbors retain the same-tier rule at every LOD boundary.
            // Only face-sharing sections depend on our leaf boundaries. Avoid
            // rebuilding the 20 unrelated diagonals in a 3x3x3 neighborhood.
            level.setSectionRangeDirty(x - 1, y, z, x + 1, y, z);
            level.setSectionRangeDirty(x, y - 1, z, x, y - 1, z);
            level.setSectionRangeDirty(x, y + 1, z, x, y + 1, z);
            level.setSectionRangeDirty(x, y, z - 1, x, y, z - 1);
            level.setSectionRangeDirty(x, y, z + 1, x, y, z + 1);
        }
        if (pruned) epoch++;
    }
}
