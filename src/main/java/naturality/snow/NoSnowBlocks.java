package naturality.snow;

import naturality.Naturality;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Blocks that cannot be replaced by snow or support a snow coating. */
public final class NoSnowBlocks {
    public static final TagKey<Block> BLOCKS = TagKey.create(Registries.BLOCK, Naturality.id("nosnow"));

    private NoSnowBlocks() { }

    public static boolean contains(BlockState state) {
        return state.is(BLOCKS);
    }
}
