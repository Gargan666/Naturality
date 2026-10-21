package naturality.blocks.waterlogging;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

/** Shared fluid state and placement behavior for additional waterloggable vanilla blocks. */
@org.jspecify.annotations.NullMarked
public final class BlockWaterlogging {
    private BlockWaterlogging() {}

    public static boolean supports(Block block) {
        return block instanceof DoorBlock || block instanceof AnvilBlock || block instanceof StonecutterBlock
            || block instanceof EnchantingTableBlock || block instanceof BellBlock
            || block instanceof FenceGateBlock || block instanceof HopperBlock || block instanceof EndRodBlock
            || block instanceof DaylightDetectorBlock || block instanceof BrewingStandBlock || block instanceof BedBlock
            || block instanceof LecternBlock;
    }

    public static boolean waterlogged(BlockState state) {
        return supports(state.getBlock()) && state.hasProperty(BlockStateProperties.WATERLOGGED)
            && state.getValue(BlockStateProperties.WATERLOGGED);
    }

    public static BlockState placement(BlockState state, BlockGetter level, BlockPos pos) {
        if (!supports(state.getBlock())) return state;
        boolean water = level.getFluidState(pos).is(FluidTags.WATER);
        return state.setValue(BlockStateProperties.WATERLOGGED, water);
    }

    public static BlockState neighborUpdate(BlockState before, BlockState after, LevelReader level,
            ScheduledTickAccess ticks, BlockPos pos) {
        if (!supports(before.getBlock())) return after;
        boolean water = waterlogged(before);
        if (water) ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if (after.isAir()) return water ? Fluids.WATER.getSource(false).createLegacyBlock() : after;
        if (after.getBlock() != before.getBlock()) return after;
        // Door updates copy the other half's entire state. Water belongs to each occupied cell independently.
        return after.setValue(BlockStateProperties.WATERLOGGED, water);
    }
}
