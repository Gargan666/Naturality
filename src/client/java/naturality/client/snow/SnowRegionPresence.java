package naturality.client.snow;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;

/** Conservative client-thread palette probe; never load chunks or inspect live terrain on workers. */
public final class SnowRegionPresence {
    private SnowRegionPresence() {}
    public static boolean mayHaveSnow(ClientLevel level, long section) {
        if (level.isDebug()) return true;
        int x = SectionPos.x(section), y = SectionPos.y(section), z = SectionPos.z(section);
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            var chunk = level.getChunkSource().getChunk(x + dx, z + dz, false);
            if (chunk == null) continue;
            for (int sy = Math.max(y - 3, level.getMinSectionY()); sy <= Math.min(y + 3, level.getMaxSectionY() - 1); sy++) {
                var data = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                if (!data.hasOnlyAir() && data.maybeHas(state -> state.is(Blocks.SNOW))) return true;
            }
        }
        return false;
    }

    public static boolean hasRootedStalk(ClientLevel level, long section) {
        var chunk = level.getChunkSource().getChunk(SectionPos.x(section), SectionPos.z(section), false);
        int y = SectionPos.y(section);
        return chunk != null && y >= level.getMinSectionY() && y < level.getMaxSectionY()
            && chunk.getSection(chunk.getSectionIndexFromSectionY(y)).maybeHas(state -> state.is(Blocks.SUGAR_CANE));
    }
}
