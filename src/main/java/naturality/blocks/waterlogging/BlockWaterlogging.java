package naturality.blocks.waterlogging;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluids;

/** Extra vanilla water containers and the cauldron's external-water versus stored-fill policy. */
@org.jspecify.annotations.NullMarked
public final class BlockWaterlogging {
    public static final BooleanProperty WATER_SURFACE = BooleanProperty.create("water_surface");

    private BlockWaterlogging() {}

    public static boolean supports(Block block) {
        return block instanceof DoorBlock || block instanceof AnvilBlock || block instanceof StonecutterBlock
            || block instanceof EnchantingTableBlock || block instanceof BellBlock || block instanceof AbstractCauldronBlock
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
        state = state.setValue(BlockStateProperties.WATERLOGGED, water);
        if (water && state.getBlock() instanceof AbstractCauldronBlock) {
            return submergedCauldron(level, pos);
        }
        return state;
    }

    private static BlockState submergedCauldron(BlockGetter level, BlockPos pos) {
        // External water replaces lava, powder snow and stored water rather than preserving an independent fill.
        return Blocks.CAULDRON.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true)
            .setValue(WATER_SURFACE, level.getBlockState(pos.above()).isAir());
    }

    public static BlockState cauldronChange(BlockState replacement, BlockGetter level, BlockPos pos) {
        if (!(replacement.getBlock() instanceof AbstractCauldronBlock)) return replacement;
        if (waterlogged(replacement)
                || waterlogged(level.getBlockState(pos)) && hasSurroundingWater(level, pos)) {
            return submergedCauldron(level, pos);
        }
        return replacement;
    }

    public static BlockState neighborUpdate(BlockState before, BlockState after, LevelReader level,
            ScheduledTickAccess ticks, BlockPos pos) {
        if (!supports(before.getBlock())) return after;
        boolean water = waterlogged(before);
        if (water) ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if (after.isAir()) return water ? Fluids.WATER.getSource(false).createLegacyBlock() : after;
        if (after.getBlock() != before.getBlock()) return after;
        // Door updates copy the other half's entire state. Water belongs to each occupied cell independently.
        after = after.setValue(BlockStateProperties.WATERLOGGED, water);
        if (!water || !(before.getBlock() instanceof AbstractCauldronBlock)) return after;
        if (hasSurroundingWater(level, pos)) return submergedCauldron(level, pos);
        return level.getBlockState(pos.above()).isAir()
            ? Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3)
            : Blocks.CAULDRON.defaultBlockState();
    }

    private static boolean hasSurroundingWater(BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            // A group of cauldrons must not keep each other waterlogged after the surrounding water is gone.
            if (!(neighbor.getBlock() instanceof AbstractCauldronBlock) && neighbor.getFluidState().is(FluidTags.WATER)) return true;
        }
        return false;
    }
}
