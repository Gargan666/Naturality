package naturality.blocks.banner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import naturality.bootstrap.ContentRegistration.NaturalityBlocks;

/** Wall attachment and selection geometry for the existing twenty-by-forty banner cloth. */
@org.jspecify.annotations.NullMarked
public final class BannerPlacement {
    public static final double CLEARANCE = 1.0 / 1024.0;
    public static final ThreadLocal<net.minecraft.world.level.ClipContext> PICK = new ThreadLocal<>();

    private BannerPlacement() {}

    public static void initialize() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register((level, chunk, _) -> {
            // Only inspect the supplied chunk here; defer world/support queries until its load completes.
            for (var entity : chunk.getBlockEntities().values()) {
                BlockState state = entity.getBlockState();
                if (state.getBlock() instanceof WallBannerBlock) level.scheduleTick(entity.getBlockPos(), state.getBlock(), 1);
            }
        });
    }

    private static VoxelShape support(BlockState state, BlockGetter level, BlockPos pos) {
        BlockPos behind = pos.relative(state.getValue(WallBannerBlock.FACING).getOpposite());
        return level.getBlockState(behind).getCollisionShape(level, behind);
    }

    public static boolean canAttach(BlockState state, BlockGetter level, BlockPos pos) {
        return !support(state, level, pos).isEmpty();
    }

    public static boolean hasRoom(LevelReader level, BlockPos pos) {
        if (pos.getY() - 1 < level.getMinY()) return false;
        BlockState below = level.getBlockState(pos.below());
        return below.getBlock() instanceof Segment || (below.canBeReplaced() && below.getFluidState().isEmpty());
    }

    public static void placeLower(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof WallBannerBlock && hasRoom(level, pos)) {
            level.setBlock(pos.below(), NaturalityBlocks.BANNER_LOWER.defaultBlockState()
                .setValue(WallBannerBlock.FACING, state.getValue(WallBannerBlock.FACING)), Block.UPDATE_ALL);
        }
    }

    public static Vec3 soundPosition(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getBlock() instanceof Segment) {
            pos = pos.above();
            state = Blocks.WALL_BANNER.white().defaultBlockState()
                .setValue(WallBannerBlock.FACING, state.getValue(WallBannerBlock.FACING));
        }
        return state.getBlock() instanceof WallBannerBlock
            ? outline(state, level, pos).bounds().getCenter().add(pos.getX(), pos.getY(), pos.getZ())
            : Vec3.atCenterOf(pos);
    }

    public static VoxelShape upperOutline(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.join(outline(state, level, pos), Shapes.box(-1, 0, -1, 2, 1, 2),
            net.minecraft.world.phys.shapes.BooleanOp.AND);
    }

    public static net.minecraft.world.phys.@org.jspecify.annotations.Nullable BlockHitResult pick(
            BlockGetter level, BlockPos cell, Vec3 from, Vec3 to,
            net.minecraft.world.phys.@org.jspecify.annotations.Nullable BlockHitResult current) {
        var context = PICK.get();
        if (context == null) return current;
        var cellBounds = new net.minecraft.world.phys.AABB(cell).inflate(1e-6);
        // The traversal must reach the cell containing the actual intersection before
        // accepting an overhanging shape; otherwise it can skip nearer geometry.
        if (current != null && current.getBlockPos().equals(cell)
                && (level.getBlockState(cell).getBlock() instanceof WallBannerBlock
                    || level.getBlockState(cell).getBlock() instanceof Segment)
                && !cellBounds.contains(current.getLocation())) current = null;
        if (!naturality.api.geometry.RaycastShapePresence.mayContain(level,
                naturality.api.geometry.RaycastShapePresence.BANNER, cell.getX()-1, cell.getY(), cell.getZ()-1,
                cell.getX()+1, cell.getY()+1, cell.getZ()+1)) return current;
        double distance = current == null ? Double.POSITIVE_INFINITY : current.getLocation().distanceToSqr(from);
        for (BlockPos candidate : BlockPos.betweenClosed(cell.offset(-1, 0, -1), cell.offset(1, 1, 1))) {
            BlockState state = level.getBlockState(candidate);
            if (!(state.getBlock() instanceof WallBannerBlock || state.getBlock() instanceof Segment)) continue;
            // betweenClosed reuses a MutableBlockPos, and BlockHitResult retains its
            // supplied position. Freeze it before the iterator advances to another cell.
            var hit = context.getBlockShape(state, level, candidate).clip(from, to, candidate.immutable());
            if (hit != null && cellBounds.contains(hit.getLocation()) && hit.getLocation().distanceToSqr(from) < distance) {
                current = hit;
                distance = hit.getLocation().distanceToSqr(from);
            }
        }
        return current;
    }

    public static double inset(BlockState state, BlockGetter level, BlockPos pos) {
        VoxelShape shape = support(state, level, pos);
        if (shape.isEmpty()) return 0.0;
        Direction facing = state.getValue(WallBannerBlock.FACING);
        double distance = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE
            ? 1.0 - shape.max(facing.getAxis()) : shape.min(facing.getAxis());
        return Math.clamp(distance, 0.0, 1.0);
    }

    public static VoxelShape outline(BlockState state, BlockGetter level, BlockPos pos) {
        // A forgiving two-pixel selection thickness around the one-pixel cloth.
        double back = CLEARANCE - inset(state, level, pos) - 1.0 / 32.0;
        double front = back + 1.0 / 8.0;
        return switch (state.getValue(WallBannerBlock.FACING)) {
            case SOUTH -> Shapes.box(-0.125, -1.5, back, 1.125, 1.0, front);
            case NORTH -> Shapes.box(-0.125, -1.5, 1.0 - front, 1.125, 1.0, 1.0 - back);
            case EAST -> Shapes.box(back, -1.5, -0.125, front, 1.0, 1.125);
            case WEST -> Shapes.box(1.0 - front, -1.5, -0.125, 1.0 - back, 1.0, 1.125);
            default -> Shapes.empty();
        };
    }

    /** Invisible second cell: reserves placement space and forwards removal to the patterned owner. */
    public static final class Segment extends Block {
        public Segment(Properties properties) {
            super(properties);
            registerDefaultState(defaultBlockState().setValue(WallBannerBlock.FACING, Direction.NORTH));
        }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(WallBannerBlock.FACING);
        }

        @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

        @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            BlockState owner = level.getBlockState(pos.above());
            if (!(owner.getBlock() instanceof WallBannerBlock)) return Shapes.empty();
            return Shapes.join(outline(owner, level, pos.above()).move(0, 1, 0),
                Shapes.box(-1, -1, -1, 2, 1, 2), net.minecraft.world.phys.shapes.BooleanOp.AND);
        }

        @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
                BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
            return level.getBlockState(pos.above()).getBlock() instanceof WallBannerBlock ? state : Blocks.AIR.defaultBlockState();
        }

        @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
            if (level.getBlockState(pos.above()).getBlock() instanceof WallBannerBlock) {
                // The owner holds all patterns and is the only block allowed to drop an item.
                if (level.isClientSide()) level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                else level.destroyBlock(pos.above(), !player.preventsBlockDrops(), player);
            }
            return state;
        }

        @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
            if (level.getBlockState(pos.above()).getBlock() instanceof WallBannerBlock) level.destroyBlock(pos.above(), true);
            super.affectNeighborsAfterRemoval(state, level, pos, moved);
        }

        @Override protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
            return level.getBlockState(pos.above()).getCloneItemStack(level, pos.above(), includeData);
        }
    }
}
