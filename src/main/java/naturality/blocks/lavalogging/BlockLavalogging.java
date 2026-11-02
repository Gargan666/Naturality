package naturality.blocks.lavalogging;

import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/** Source lava stored in the block state, using vanilla's liquid-container lifecycle. */
@org.jspecify.annotations.NullMarked
public final class BlockLavalogging {
    public static final BooleanProperty LAVALOGGED = BooleanProperty.create("lavalogged");
    private static boolean fireRulesReady;

    private BlockLavalogging() {}

    public static void initialize() {
        // Vanilla and mod fire registrations happen after the block-state tables are constructed.
        fireRulesReady = true;
    }

    public static boolean lavalogged(BlockState state) {
        return state.hasProperty(LAVALOGGED) && state.getValue(LAVALOGGED);
    }

    public static boolean supports(BlockState state) {
        if (!state.hasProperty(LAVALOGGED) || state.ignitedByLava()) return false;
        if (state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) return false;
        var fuel = FlammableBlockRegistry.getDefaultInstance().get(state.getBlock());
        return fuel == null || (fuel.getBurnOdds() == 0 && fuel.getIgniteOdds() == 0);
    }

    public static boolean canFill(BlockState state, Fluid fluid) {
        return fluid == Fluids.LAVA && supports(state) && !lavalogged(state)
            && !state.getValue(BlockStateProperties.WATERLOGGED);
    }

    public static boolean fill(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        if (!fluid.isSource() || !canFill(state, fluid.getType())) return false;
        if (!level.isClientSide()) {
            level.setBlockAndUpdate(pos, state.setValue(LAVALOGGED, true));
            level.scheduleTick(pos, Fluids.LAVA, Fluids.LAVA.getTickDelay(level));
        }
        return true;
    }

    public static ItemStack pickup(LevelAccessor level, BlockPos pos, BlockState state) {
        BlockState dry = state.setValue(LAVALOGGED, false);
        level.setBlockAndUpdate(pos, dry);
        if (!dry.canSurvive(level, pos)) level.destroyBlock(pos, true);
        return new ItemStack(Items.LAVA_BUCKET);
    }

    public static BlockState placement(BlockState state, BlockGetter level, BlockPos pos) {
        if (!state.hasProperty(LAVALOGGED)) return state;
        // Combining slabs removes the space that held either fluid.
        if (!supports(state)) return state.setValue(LAVALOGGED, false);
        FluidState previous = level.getFluidState(pos);
        return previous.is(Fluids.LAVA) && previous.isSource()
            ? state.setValue(LAVALOGGED, true) : state;
    }

    public static BlockState neighborUpdate(BlockState before, BlockState after, LevelReader level,
            ScheduledTickAccess ticks, BlockPos pos) {
        if (!before.hasProperty(LAVALOGGED)) return after;
        boolean lava = lavalogged(before);
        if (lava) ticks.scheduleTick(pos, Fluids.LAVA, Fluids.LAVA.getTickDelay(level));
        if (after.isAir()) return lava ? Fluids.LAVA.getSource(false).createLegacyBlock() : after;
        if (after.getBlock() != before.getBlock()) {
            // For example, a living coral becomes its dead, still fluid-holding counterpart.
            return lava && supports(after) ? after.setValue(LAVALOGGED, true) : after;
        }
        // Doors copy their other half's state; the fluid belongs to this cell.
        return after.setValue(LAVALOGGED, lava);
    }

    public static BlockState exclusive(BlockState state, Property<?> changed) {
        if (!lavalogged(state)) return state;
        if (fireRulesReady && !supports(state)) return state.setValue(LAVALOGGED, false);
        if (!state.getValue(BlockStateProperties.WATERLOGGED)) return state;
        return changed == BlockStateProperties.WATERLOGGED
            ? state.setValue(LAVALOGGED, false) : state.setValue(BlockStateProperties.WATERLOGGED, false);
    }
}
