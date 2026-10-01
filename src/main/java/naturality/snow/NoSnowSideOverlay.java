package naturality.snow;

import naturality.Naturality;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Blocks that retain snow coverage but do not receive the side drip overlay. */
public final class NoSnowSideOverlay {
    public static final TagKey<Block> BLOCKS = TagKey.create(Registries.BLOCK, Naturality.id("no_snow_side_overlay"));

    private NoSnowSideOverlay() { }

    public static boolean contains(BlockState state) {
        return state.is(BLOCKS);
    }
}
