package naturality.snow;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;

/** Rendered model surfaces, independent of selection and collision hitboxes. */
public final class SnowSupportModels {
    private SnowSupportModels() { }

    public static List<AABB> boxes(BlockGetter level, BlockPos pos) {
        return SnowBlockModels.boxes(level.getBlockState(pos));
    }
}