package naturality.client.fluid;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.chunk.LevelChunk;

/** Palette presence is conservative; cache it only across one synchronous scan batch. */
public final class FluidSectionScan {
    private final Long2ByteOpenHashMap water = new Long2ByteOpenHashMap();
    private final Long2ByteOpenHashMap lava = new Long2ByteOpenHashMap();
    public void reset() { water.clear(); lava.clear(); }
    public boolean contains(LevelChunk chunk, int sectionY, boolean molten) {
        var cache = molten ? lava : water;
        long key = SectionPos.asLong(chunk.getPos().x(), sectionY, chunk.getPos().z());
        byte cached = cache.get(key);
        if (cached != 0) return cached == 1;
        int index = chunk.getSectionIndexFromSectionY(sectionY);
        boolean found = index >= 0 && index < chunk.getSectionsCount()
            && chunk.getSection(index).maybeHas(state -> state.getFluidState().is(molten ? FluidTags.LAVA : FluidTags.WATER));
        cache.put(key, found ? (byte)1 : (byte)2);
        return found;
    }
}
