package naturality.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Availability checks must never request generation or load an absent chunk. */
public final class LoadedChunks {
    private LoadedChunks() { }

    public static boolean has(Level level, BlockPos pos) {
        // Even getChunk(..., false) can wait for an existing server chunk future.
        // getChunkNow only inspects chunks which have already completed loading.
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }
}
