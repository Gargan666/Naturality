package naturality.blocks.lattice;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WaterloggedTransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A centered lattice plane surrounded by two pixels of collision thickness. */
@org.jspecify.annotations.NullMarked
public final class IronLatticeBlock extends WaterloggedTransparentBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    private static final VoxelShape HORIZONTAL = Block.box(0, 7, 0, 16, 9, 16);
    private static final VoxelShape NORTH_SOUTH = Block.box(0, 0, 7, 16, 16, 9);
    private static final VoxelShape EAST_WEST = Block.box(7, 0, 0, 9, 16, 16);

    public IronLatticeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public @org.jspecify.annotations.Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        var player = context.getPlayer();
        Direction facing = context.getClickedFace();
        if (player != null) {
            Vec3 towardPlayer = player.getEyePosition().subtract(Vec3.atCenterOf(context.getClickedPos()));
            // Normalizing does not change which axis has the largest dot product.
            facing = Direction.getApproximateNearest(towardPlayer);
        }
        return defaultBlockState().setValue(FACING, facing)
            .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case DOWN, UP -> HORIZONTAL;
            case NORTH, SOUTH -> NORTH_SOUTH;
            case WEST, EAST -> EAST_WEST;
        };
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        // Like slabs, lattice cells are collision obstacles rather than open air.
        // Ground navigation then finds the walkable cell above and uses the real
        // collision height (9/16 for horizontal panels) as its floor level.
        return type == PathComputationType.WATER && state.getFluidState().is(FluidTags.WATER);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
        // Adjacent lattices can occupy different faces; sharing a block type must not hide either plane.
        return false;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
