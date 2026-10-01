package naturality.snow;

import naturality.Naturality;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Blocks snow passes through to their support without coating the block. */
public final class SnowSupportOnly {
    public static final TagKey<Block> BLOCKS = TagKey.create(Registries.BLOCK, Naturality.id("snow_support_only"));

    private SnowSupportOnly() { }

    public static boolean contains(BlockState state) {
        return state.is(BLOCKS) && (!(state.getBlock() instanceof CampfireBlock)
            || state.getValue(CampfireBlock.LIT));
    }
}
