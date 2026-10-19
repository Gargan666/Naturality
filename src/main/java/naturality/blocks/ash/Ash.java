package naturality.blocks.ash;

import naturality.Naturality;
import naturality.api.blocks.FallingLayerBlock;
import naturality.api.geometry.ShapeRecursionGuard;
import naturality.api.geometry.SurfaceLayers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Dry, falling ash coatings; the full ash block uses vanilla sand behavior. */
@org.jspecify.annotations.NullMarked
public final class Ash {
    public static final TagKey<Block> NO_OVERLAYS = TagKey.create(Registries.BLOCK, Naturality.id("no_ash_overlays"));

    private Ash() {}

    public static boolean isLayer(BlockState state) {
        return state.getBlock() instanceof Layer;
    }

    public static final class Layer extends FallingLayerBlock {
        public Layer(BlockBehaviour.Properties properties) { super(properties); }

        @Override
        public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) { return 0x777777; }

        @Override
        public boolean washedAwayBy(net.minecraft.world.level.material.FluidState fluid) {
            return fluid.is(net.minecraft.tags.FluidTags.WATER);
        }

        @Override
        public int maximumLayers(BlockGetter level, BlockPos pos) {
            return SurfaceLayers.surfaces(level,pos,this).stream().anyMatch(patch -> patch.y() < -1e-5) ? 7 : 8;
        }

        private VoxelShape fitted(BlockState state, BlockGetter level, BlockPos pos, boolean collision) {
            // Unsupported blocks and falling entities use their ordinary, unshifted layer model.
            if (ShapeRecursionGuard.active() || SurfaceLayers.surfaces(level, pos, this).isEmpty()) return collision
                ? (state.getValue(LAYERS) == 1 ? Shapes.empty()
                    : Block.column(16, 0, (state.getValue(LAYERS) - 1) * 2))
                : super.getShape(state, level, pos, CollisionContext.empty());
            return collision ? SurfaceLayers.collisionShape(level, pos, state.getValue(LAYERS), this)
                : SurfaceLayers.shape(level, pos, state.getValue(LAYERS), this);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return fitted(state, level, pos, false);
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return fitted(state, level, pos, true);
        }

        @Override
        protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
            return fitted(state, level, pos, false);
        }

        @Override
        protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SurfaceLayers.usesVanillaGeometry(level, pos, this) ? super.getVisualShape(state, level, pos, context) : Shapes.empty();
        }

        @Override
        protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return !hasPartialLayerBelow(level,pos) && !SurfaceLayers.surfaces(level, pos, this).isEmpty();
        }

        @Override
        public @org.jspecify.annotations.Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
            BlockState state = super.getStateForPlacement(context);
            if (state == null) return null;
            return state.setValue(LAYERS,Math.min(maximumLayers(context.getLevel(),context.getClickedPos()),state.getValue(LAYERS)));
        }

        @Override
        protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
            if (context.getItemInHand().is(asItem()) && state.getValue(LAYERS) >= 7
                    && SurfaceLayers.surfaces(context.getLevel(), context.getClickedPos(), this).stream()
                        .anyMatch(patch -> patch.y() < -1e-5)) return false;
            return super.canBeReplaced(state, context);
        }
    }

    /** Preserve replaceable plants beneath the coating, just as fitted snow does. */
    public static BlockPlaceContext placementContext(BlockPlaceContext context) {
        BlockPos target = context.getClickedPos();
        BlockState current = context.getLevel().getBlockState(target);
        if (isLayer(current)) return context;
        int depth = 0;
        while (depth < SurfaceLayers.MAX_DEPTH && naturality.blocks.snow.SnowGeometry.isFoliage(current)) {
            target = target.above();
            current = context.getLevel().getBlockState(target);
            depth++;
        }
        return depth == 0 ? context : BlockPlaceContext.at(context,target,net.minecraft.core.Direction.UP);
    }
}
