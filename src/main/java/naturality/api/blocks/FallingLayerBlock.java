package naturality.api.blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.FallingBlock;


import net.minecraft.world.item.ItemStack;

import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.server.level.ServerLevel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Shared stacking, fluid state and gravity for granular layers; materials own their fitted geometry. */
@org.jspecify.annotations.NullMarked
public class FallingLayerBlock extends FallingBlock {
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape[] SHAPES = Block.boxes(8, height -> Block.column(16.0, 0.0, height * 2));

    public FallingLayerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 1));
    }

    /** Older saved stacks need a gravity tick too; loading a chunk does not call onPlace. */
    public static void initialize() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register((level,chunk,_) -> {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int index = 0; index < chunk.getSectionsCount(); index++) {
                var section = chunk.getSection(index);
                if (!section.maybeHas(state -> state.getBlock() instanceof FallingLayerBlock)) continue;
                int bottom = chunk.getSectionYFromSectionIndex(index) * 16;
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    BlockState state = section.getBlockState(x,y,z);
                    if (!(state.getBlock() instanceof FallingLayerBlock layers)) continue;
                    pos.set(chunk.getPos().getMinBlockX()+x,bottom+y,chunk.getPos().getMinBlockZ()+z);
                    // CHUNK_LOAD runs before the FULL chunk future completes. Querying
                    // the world here can wait on that same future. The normal gravity
                    // tick checks support after loading, including for older saved stacks.
                    level.scheduleTick(pos.immutable(),layers,layers.getDelayAfterPlace());
                }
            }
        });
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type == PathComputationType.LAND && state.getValue(LAYERS) < 5;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LAYERS) == 8 ? 0.2F : 1.0F;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return !hasPartialLayerBelow(level,pos)
            && Block.isFaceFull(below.getBlockSupportShape(level,pos.below()),Direction.UP);
    }

    protected final boolean hasPartialLayerBelow(BlockGetter level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.getBlock() instanceof FallingLayerBlock && below.getValue(LAYERS) < 8;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (state.hasProperty(WATERLOGGED) && state.getValue(WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        int layers = state.getValue(LAYERS);
        if (!context.getItemInHand().is(asItem()) || layers >= 8) return layers == 1;
        return !context.replacingClickedOnBlock() || context.getClickedFace() == Direction.UP;
    }

    @Override
    public @org.jspecify.annotations.Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        if (washedAwayBy(context.getLevel().getFluidState(context.getClickedPos()))) return null;
        BlockState previous = context.getLevel().getBlockState(context.getClickedPos());
        if (previous.is(this)) return previous.setValue(LAYERS, Math.min(8, previous.getValue(LAYERS) + 1));
        return defaultBlockState().hasProperty(WATERLOGGED) ? defaultBlockState().setValue(WATERLOGGED,
            context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER)) : defaultBlockState();
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.hasProperty(WATERLOGGED) && state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!canSurvive(state, level, pos) && pos.getY() >= level.getMinY()) {
            FallingBlockEntity.fall(level, pos, state);
        }
    }

    @Override
    public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getMapColor(level, pos).col;
    }

    public int maximumLayers(BlockGetter level, BlockPos pos) { return 8; }

    public boolean washedAwayBy(FluidState fluid) { return false; }

    /** Settle granular layers above partial supports and merge without discarding inventory layers. */
    public static boolean settle(FallingBlockEntity entity) {
        BlockState falling = entity.getBlockState();
        if (!(falling.getBlock() instanceof FallingLayerBlock layers)
                || !(entity.level() instanceof ServerLevel level)) return false;
        if (layers.washedAwayBy(level.getFluidState(entity.blockPosition()))) {
            if (entity.dropItem && level.getGameRules().get(GameRules.ENTITY_DROPS))
                Block.popResource(level,entity.blockPosition(),new ItemStack(falling.getBlock(),falling.getValue(LAYERS)));
            entity.discard();
            return true;
        }
        if (!entity.onGround()) return false;
        BlockPos pos = entity.blockPosition();
        BlockState current = level.getBlockState(pos);
        if (current.is(falling.getBlock()) && current.getValue(LAYERS) == 8
                || !current.is(falling.getBlock()) && !current.canBeReplaced()) {
            pos = pos.above();
            current = level.getBlockState(pos);
        }
        int count = falling.getValue(LAYERS);
        if (current.is(falling.getBlock()) && current.getValue(LAYERS) < 8) {
            int capacity = layers.maximumLayers(level,pos);
            int added = Math.min(count, Math.max(0, capacity - current.getValue(LAYERS)));
            if (added > 0) level.setBlockAndUpdate(pos,current.setValue(LAYERS,current.getValue(LAYERS)+added));
            count -= added;
            pos = pos.above();
            current = level.getBlockState(pos);
        }
        if (count > 0 && current.canBeReplaced() && falling.canSurvive(level,pos)) {
            int placed = Math.min(count,layers.maximumLayers(level,pos));
            BlockState settled = falling.setValue(LAYERS,placed);
            if (settled.hasProperty(WATERLOGGED)) settled = settled.setValue(WATERLOGGED,level.getFluidState(pos).is(Fluids.WATER));
            if (level.setBlockAndUpdate(pos,settled)) count -= placed;
        }
        if (count > 0 && entity.dropItem && level.getGameRules().get(GameRules.ENTITY_DROPS)) {
            Block.popResource(level,pos,new ItemStack(falling.getBlock(),count));
        }
        entity.discard();
        return true;
    }
}
