package naturality.client.rendering.fog;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Persistent world-anchored lake samples; updates replace only the affected texture entries. */
@org.jspecify.annotations.NullMarked
final class NetherLavaFogSurvey {
    // Survey the same 31-column area, but retain departing samples throughout the
    // shader's 288-block ray range so cache reuse cannot truncate a visible fade.
    private static final int SCAN_SIZE = 31, SIZE = 79, CELLS = SIZE * SIZE, STEP = 8, BUDGET = 32;
    private static final float FADE_SECONDS = 1.5F;
    private static final int[] keysX = new int[CELLS], keysZ = new int[CELLS];
    private static final float[] surfaces = new float[CELLS], ceilings = new float[CELLS], coverage = new float[CELLS];
    // Positive: fade-in origin. Negative: -(fade-out origin + 1), reserving zero.
    private static final float[] discovered = new float[CELLS];
    private static final boolean[] dirty = new boolean[CELLS], active = new boolean[CELLS];
    private static final @Nullable LevelChunk[] chunks = new LevelChunk[CELLS];
    private static final Integer[] order = new Integer[SCAN_SIZE * SCAN_SIZE];
    private static final ByteBuffer columnData = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
    private static @Nullable ClientLevel world;
    private static @Nullable GpuTexture columnTexture;
    private static @Nullable GpuTextureView columnView;
    private static long scanned = Long.MIN_VALUE, epoch = System.nanoTime();
    private static int cursor;

    static {
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingInt(i -> {
            int x = i % SCAN_SIZE - SCAN_SIZE / 2, z = i / SCAN_SIZE - SCAN_SIZE / 2;
            return x * x + z * z;
        }));
    }

    private NetherLavaFogSurvey() {}

    static void checkWorld(@Nullable ClientLevel level) {
        if (world != level) reset(level);
    }

    static void reset(@Nullable ClientLevel level) {
        world = level;
        Arrays.fill(keysX, Integer.MIN_VALUE);
        Arrays.fill(coverage, 0);
        Arrays.fill(chunks, null);
        Arrays.fill(dirty, true);
        Arrays.fill(active, false);
        scanned = Long.MIN_VALUE;
        epoch = System.nanoTime();
        cursor = 0;
        if (columnView != null) { columnView.close(); columnView = null; }
        if (columnTexture != null) { columnTexture.close(); columnTexture = null; }
    }

    private static int slot(int x, int z) {
        return Math.floorMod(z, SIZE) * SIZE + Math.floorMod(x, SIZE);
    }

    static void invalidate(ClientLevel level, BlockPos position) {
        if (world != level) return;
        int x = Math.floorDiv(position.getX(), STEP), z = Math.floorDiv(position.getZ(), STEP);
        int slot = slot(x, z);
        if (keysX[slot] == x && keysZ[slot] == z) dirty[slot] = true;
    }

    static void invalidate(ClientLevel level, ChunkPos position) {
        if (world != level) return;
        for (int z = position.z() * 2; z < position.z() * 2 + 2; z++)
            for (int x = position.x() * 2; x < position.x() * 2 + 2; x++) {
                int slot = slot(x, z);
                if (keysX[slot] == x && keysZ[slot] == z) {
                    dirty[slot] = true;
                    chunks[slot] = null;
                }
            }
    }

    private static void scan(ClientLevel level, int centerX, int centerZ) {
        long tick = level.getGameTime();
        if (scanned == tick) return;
        scanned = tick;
        int budget = BUDGET;
        for (int index : order) {
            int x = centerX + index % SCAN_SIZE - SCAN_SIZE / 2, z = centerZ + index / SCAN_SIZE - SCAN_SIZE / 2;
            int slot = slot(x, z);
            var chunk = level.getChunkSource().getChunkNow((x * STEP) >> 4, (z * STEP) >> 4);
            // Pending/reloaded chunks keep their last confirmed lake data. Never erase
            // visible samples just because their replacement has not arrived yet.
            if (chunk == null || (!dirty[slot] && chunks[slot] == chunk)) continue;
            survey(level, chunk, x, z);
            if (--budget == 0) return;
        }
        // Catch edits without normal block notifications, without rebuilding the map.
        for (int i = 0; i < Math.min(4, budget); i++) {
            int index = order[cursor++];
            if (cursor == order.length) cursor = 0;
            int x = centerX + index % SCAN_SIZE - SCAN_SIZE / 2, z = centerZ + index / SCAN_SIZE - SCAN_SIZE / 2;
            var chunk = level.getChunkSource().getChunkNow((x * STEP) >> 4, (z * STEP) >> 4);
            if (chunk != null) survey(level, chunk, x, z);
        }
    }

    private static void survey(ClientLevel level, LevelChunk chunk, int x, int z) {
        int slot = slot(x, z);
        int minY = level.getMinY(), maxY = level.getMaxY();
        float surface = 0, ceiling = 0;
        int hits = 0;
        for (int subZ = 0; subZ < 2; subZ++) for (int subX = 0; subX < 2; subX++) {
            int localX = (x * STEP + subX * 4) & 15, localZ = (z * STEP + subZ * 4) & 15;
            int openTop = maxY;
            // Full build-height survey: vertical camera motion never invalidates data.
            for (int y = maxY - 1; y >= minY; y--) {
                var section = chunk.getSection(chunk.getSectionIndex(y));
                if (section.hasOnlyAir()) { y = Math.max(minY, y & ~15); continue; }
                var state = section.getBlockState(localX + 2, y & 15, localZ + 2);
                var fluid = state.getFluidState();
                if (fluid.is(FluidTags.LAVA) && !fluid.getValue(FlowingFluid.FALLING) && openTop - y > 8) {
                    float height = y + fluid.getOwnHeight();
                    if (hits == 0 || Math.abs(height - surface) <= 4) {
                        surface = hits == 0 ? height : Math.max(surface, height);
                        float top = Math.min(openTop, height + 40);
                        ceiling = hits == 0 ? top : Math.min(ceiling, top);
                        hits++;
                    }
                    break;
                }
                if (state.isSolidRender() || !fluid.isEmpty()) openTop = y;
            }
        }
        chunks[slot] = chunk;
        dirty[slot] = false;
        float strength = hits / 4.0F;
        float now = elapsedSeconds();
        if (strength == 0) {
            retire(slot, now);
            return;
        }
        if (discovered[slot] >= 0 && surfaces[slot] == surface && ceilings[slot] == ceiling && coverage[slot] == strength) return;
        if (discovered[slot] < 0) {
            // Reverse a pending removal at its current brightness, without a flash
            // or a jump back to the start of the arrival animation.
            float fraction = Math.clamp(strengthAt(slot, now) / strength, 0, 1);
            float progress = (float)(0.5 - Math.sin(Math.asin(1 - 2 * (double)fraction) / 3));
            discovered[slot] = now - FADE_SECONDS * progress;
        } else if (coverage[slot] == 0) discovered[slot] = now;
        surfaces[slot] = surface;
        ceilings[slot] = ceiling;
        coverage[slot] = strength;
        upload(slot);
    }

    private static float strengthAt(int slot, float now) {
        boolean retiring = discovered[slot] < 0;
        float age = retiring ? now + discovered[slot] + 1 : now - discovered[slot];
        float progress = Math.clamp(age / FADE_SECONDS, 0, 1);
        float eased = progress * progress * (3 - 2 * progress);
        return coverage[slot] * (retiring ? 1 - eased : eased);
    }

    private static void retire(int slot, float now) {
        if (coverage[slot] == 0 || discovered[slot] < 0) return;
        coverage[slot] = strengthAt(slot, now);
        discovered[slot] = -(now + 1);
        // Keep the old height and roof until the outgoing atmosphere is invisible.
        upload(slot);
    }

    private static float elapsedSeconds() {
        // Leave room to backdate a resumed arrival without crossing the negative
        // timestamp range reserved for departures, even just after world entry.
        return FADE_SECONDS + (System.nanoTime() - epoch) / 1_000_000_000F;
    }

    private static void upload(int slot) {
        var texture = columnTexture;
        if (texture == null) return;
        columnData.clear();
        columnData.putFloat(surfaces[slot]).putFloat(ceilings[slot])
            .putFloat(coverage[slot]).putFloat(discovered[slot]).flip();
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, columnData,
            0, 0, slot % SIZE, slot / SIZE, 1, 1);
    }

    static @Nullable GpuTextureView prepare(ClientLevel level, Vec3 position, ByteBuffer data) {
        int centerX = (int)Math.floor(position.x / STEP), centerZ = (int)Math.floor(position.z / STEP);
        int startX = centerX - SIZE / 2, startZ = centerZ - SIZE / 2;
        if (columnTexture == null) {
            var columns = RenderSystem.getDevice().createTexture(() -> "Naturality fog lake heights", 5,
                GpuFormat.RGBA32_FLOAT, SIZE, SIZE, 1, 1);
            columnTexture = columns;
            columnView = RenderSystem.getDevice().createTextureView(columns);
            // Initialize once; subsequent writes affect individual changed samples.
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(columns,
                ByteBuffer.allocateDirect(CELLS * 16), 0, 0, 0, 0, SIZE, SIZE);
        }
        float now = elapsedSeconds();
        // The outer cache keeps outgoing samples at their original world positions.
        // Slot reuse happens beyond the visible ray range, after leaving the survey.
        for (int z = startZ; z < startZ + SIZE; z++) for (int x = startX; x < startX + SIZE; x++) {
            int slot = slot(x, z);
            if (keysX[slot] != x || keysZ[slot] != z) {
                boolean visible = coverage[slot] > 0;
                keysX[slot] = x;
                keysZ[slot] = z;
                surfaces[slot] = ceilings[slot] = coverage[slot] = discovered[slot] = 0;
                chunks[slot] = null;
                dirty[slot] = true;
                active[slot] = false;
                if (visible) upload(slot);
            }
            boolean wanted = Math.abs(x - centerX) <= SCAN_SIZE / 2 && Math.abs(z - centerZ) <= SCAN_SIZE / 2;
            if (!wanted) {
                retire(slot, now);
                chunks[slot] = null;
            } else if (!active[slot]) dirty[slot] = true;
            active[slot] = wanted;
        }
        scan(level, centerX, centerZ);
        float lowest = Float.POSITIVE_INFINITY, highest = Float.NEGATIVE_INFINITY;
        for (int slot = 0; slot < CELLS; slot++) if (coverage[slot] > 0) {
            if (discovered[slot] < 0 && now + discovered[slot] + 1 >= FADE_SECONDS) {
                surfaces[slot] = ceilings[slot] = coverage[slot] = discovered[slot] = 0;
                upload(slot);
                continue;
            }
            lowest = Math.min(lowest, surfaces[slot] - 1);
            highest = Math.max(highest, ceilings[slot]);
        }
        if (lowest == Float.POSITIVE_INFINITY) return null;
        data.clear();
        data.putFloat((float)(startX * STEP + 4 - position.x)).putFloat((float)(startZ * STEP + 4 - position.z))
            .putFloat(STEP).putFloat(SIZE);
        data.putFloat((float)(lowest - position.y)).putFloat((float)(highest - position.y)).putFloat(FADE_SECONDS).putFloat(0);
        data.putFloat(Math.floorMod(startX, SIZE)).putFloat(Math.floorMod(startZ, SIZE))
            .putFloat((float)position.y).putFloat(elapsedSeconds());
        data.flip();
        return columnView;
    }
}
