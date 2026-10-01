package naturality.client.snow;

import java.util.Map;
import net.minecraft.client.renderer.chunk.SectionCopy;

/** Additional immutable section copies used by fitted snow on meshing workers. */
public interface SnowRenderRegion {
    void naturality$setSnowSections(Map<Long, SectionCopy> sections);
}
