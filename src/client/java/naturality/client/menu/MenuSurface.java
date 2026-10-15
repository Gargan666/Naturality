package naturality.client.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Builds the preview payload directly from exposed columns; buried palettes never reach the client. */
public final class MenuSurface {
    /** A conservative band below the exposed surface protects ledges and foundations. */
    public static final int SURFACE_DEPTH = 24;
    private static final int BORDER = 2, STRIDE = 16 + BORDER * 2;
    public static LevelChunk snapshot(LevelChunk source) {
        return snapshot(source, (x, z) -> source.getLevel().getChunkSource().getChunkForLighting(x, z));
    }
    /** The lighting view includes completed terrain before neighboring chunks become FULL. */
    public static LevelChunk snapshot(LevelChunk source, java.util.function.BiFunction<Integer, Integer, LightChunk> neighbors) {
        var level = source.getLevel();
        var shell = new LevelChunk(level, source.getPos());
        var sections = shell.getSections();
        for (int i = 0; i < sections.length; i++)
            sections[i] = new LevelChunkSection(sections[i].getStates(), source.getSection(i).getBiomes().copy());
        // Empty packets outside the displayed footprint expose a continuous outer
        // row of side faces. Those fully fogged support faces form the skirt.
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && !MenuPreview.containsChunk(source.getPos().x(), source.getPos().z(), serverLevel.getRespawnData().pos()))
            return shell;
        int[] heights = new int[STRIDE * STRIDE];
        int ox = source.getPos().getMinBlockX(), oz = source.getPos().getMinBlockZ();
        for (int z = -BORDER; z < 16 + BORDER; z++) for (int x = -BORDER; x < 16 + BORDER; x++) {
            int wx = ox + x, wz = oz + z;
            var neighbor = x >= 0 && x < 16 && z >= 0 && z < 16
                    ? source : neighbors.apply(wx >> 4, wz >> 4);
            // Never request/generate neighbors merely to trim an edge.
            // An unavailable neighbor must use the adjacent local column, not the opposite edge.
            heights[(z + BORDER) * STRIDE + x + BORDER] = neighbor == null
                    ? surface(source, Math.max(0, Math.min(15, x)), Math.max(0, Math.min(15, z)))
                    : surface(neighbor, wx & 15, wz & 15, wx & ~15, wz & ~15);
        }
        var pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int index = (z + BORDER) * STRIDE + x + BORDER;
            int bottom = heights[index];
            // Include diagonal and recessed cliff feet, not just four immediate neighbors.
            for (int dz = -BORDER; dz <= BORDER; dz++) for (int dx = -BORDER; dx <= BORDER; dx++)
                bottom = Math.min(bottom, heights[index + dz * STRIDE + dx]);
            int highest = source.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            // Retain the visible band plus two hidden support blocks for sealed cut faces.
            bottom = Math.max(source.getMinY(), bottom - SURFACE_DEPTH - 2);
            for (int y = bottom; y <= highest; y++) {
                var state = source.getBlockState(pos.set(ox + x, y, oz + z));
                if (!state.isAir()) sections[shell.getSectionIndex(y)].setBlockState(x, y & 15, z, state, false);
            }
        }
        source.getHeightmaps().forEach(entry -> shell.setHeightmap(entry.getKey(), entry.getValue().getRawData().clone()));
        source.getBlockEntities().forEach((position, entity) -> {
            int index = ((position.getZ() & 15) + BORDER) * STRIDE + (position.getX() & 15) + BORDER;
            if (position.getY() >= heights[index] && !shell.getBlockState(position).isAir())
                shell.getBlockEntities().put(position, entity);
        });
        return shell;
    }
    public static int surface(ChunkAccess chunk, int x, int z) {
        return surface(chunk, x, z, chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ());
    }
    private static int surface(LightChunk chunk, int x, int z, int originX, int originZ) {
        var pos = new BlockPos.MutableBlockPos();
        int wx = originX + x, wz = originZ + z;
        int top = chunk instanceof ChunkAccess access ? access.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) : chunk.getMaxY();
        int first = Integer.MIN_VALUE, run = 0;
        for (int y = top; y >= chunk.getMinY(); y--) {
            // Do not follow a thin floating surface arbitrarily far into deep caves.
            if (first != Integer.MIN_VALUE && y < first - 48) break;
            var state = chunk.getBlockState(pos.set(wx, y, wz));
            // Roofs and thin overhangs are not the ground. Seek a supported surface,
            // preserving everything above it, including the full transparent water column.
            if (state.getFluidState().isEmpty() && !state.isAir() && (state.canOcclude() || run > 0)
                    && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)) {
                if (first == Integer.MIN_VALUE) first = y;
                if (++run == 8) return y + 7;
            } else run = 0;
        }
        return first == Integer.MIN_VALUE ? chunk.getMinY() : first;
    }
    private MenuSurface() { }
}
