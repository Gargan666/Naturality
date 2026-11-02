package naturality.blocks.lattice;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A full-face lattice with one pixel of collision extending into its block. */
@org.jspecify.annotations.NullMarked
public final class IronLatticeBlock extends WaterloggedTransparentBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    private static final VoxelShape DOWN = Block.box(0, 0, 0, 16, 1, 16);
    private static final VoxelShape UP = Block.box(0, 15, 0, 16, 16, 16);
    private static final VoxelShape NORTH = Block.box(0, 0, 0, 16, 16, 1);
    private static final VoxelShape SOUTH = Block.box(0, 0, 15, 16, 16, 16);
    private static final VoxelShape WEST = Block.box(0, 0, 0, 1, 16, 16);
    private static final VoxelShape EAST = Block.box(15, 0, 0, 16, 16, 16);

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
            case DOWN -> DOWN;
            case UP -> UP;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
        };
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
