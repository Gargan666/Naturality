package naturality.blocks.chorus.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.blocks.chorus.ChorusFlowers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.block.ChorusPlantBlock;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class ChorusMixins {
    private ChorusMixins() {}

    @Mixin(ChorusFlowerBlock.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ChorusFlowerMixin extends Block {
        protected ChorusFlowerMixin(BlockBehaviour.Properties properties) { super(properties); }

        @Override
        protected net.minecraft.world.level.block.SoundType getSoundType(BlockState state) {
            return net.minecraft.world.level.block.SoundType.ROOTS;
        }

        @Inject(method = "createBlockStateDefinition", at = @At("TAIL"))
        private void naturality$properties(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
            builder.add(ChorusFlowers.FACING, ChorusFlowers.BLOOM);
        }

        @Inject(method = "<init>", at = @At("TAIL"))
        private void naturality$defaults(Block plant, BlockBehaviour.Properties properties, CallbackInfo ci) {
            registerDefaultState(defaultBlockState().setValue(ChorusFlowers.FACING, Direction.UP)
                .setValue(ChorusFlowers.BLOOM, false));
        }

        @Override public @org.jspecify.annotations.Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
            return ChorusFlowers.flower(context.getClickedFace(), 0, context.getLevel().getRandom());
        }

        @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return ChorusFlowers.shape(state);
        }

        @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return ChorusFlowers.shape(state);
        }

        @Inject(method = "getBlockSupportShape", at = @At("HEAD"), cancellable = true)
        private void naturality$supportShape(BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
            cir.setReturnValue(ChorusFlowers.shape(state));
        }

        @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
        private void naturality$survive(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
            // Preserve vanilla's lateral support fallback for flowers in older saves.
            if (state.getValue(ChorusFlowers.FACING) != Direction.UP || ChorusFlowers.supported(state, level, pos))
                cir.setReturnValue(ChorusFlowers.supported(state, level, pos));
        }

        @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
        private void naturality$neighbor(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                Direction direction, BlockPos neighbor, BlockState neighborState, RandomSource random,
                CallbackInfoReturnable<BlockState> cir) {
            if (!state.canSurvive(level, pos)) ticks.scheduleTick(pos, this, 1);
            cir.setReturnValue(state);
        }

        @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
        private void naturality$growth(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
            // Older lateral flowers have no facing saved. Adopt their existing stem
            // direction without rerolling the saved variant before advancing them.
            if (!ChorusFlowers.supported(state, level, pos) && state.canSurvive(level, pos)) {
                for (Direction direction : Direction.values()) {
                    if (level.getBlockState(pos.relative(direction)).is(Blocks.CHORUS_PLANT)) {
                        state = state.setValue(ChorusFlowers.FACING, direction.getOpposite());
                        level.setBlock(pos, state, 2);
                        break;
                    }
                }
            }
            ChorusFlowers.grow(state, level, pos, random);
            ci.cancel();
        }

        @Override protected BlockState rotate(BlockState state, Rotation rotation) {
            return state.setValue(ChorusFlowers.FACING, rotation.rotate(state.getValue(ChorusFlowers.FACING)));
        }

        @Override protected BlockState mirror(BlockState state, Mirror mirror) {
            return state.setValue(ChorusFlowers.FACING, mirror.mirror(state.getValue(ChorusFlowers.FACING)));
        }

        @WrapOperation(method = "growTreeRecursive", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
        private static boolean naturality$generatedFlowers(LevelAccessor level, BlockPos pos, BlockState state, int flags,
                Operation<Boolean> original) {
            if (state.is(Blocks.CHORUS_FLOWER)) {
                // Keep the authored tree, but put its terminal flower on a free face of the tip.
                Direction facing = Direction.values()[level.getRandom().nextInt(6)];
                BlockPos target = pos.relative(facing);
                if (facing != Direction.UP && !level.isOutsideBuildHeight(target) && level.isEmptyBlock(target)) {
                    BlockState flower = ChorusFlowers.flower(facing, 5, level.getRandom());
                    boolean placed = original.call(level, target, flower, flags);
                    if (placed) ChorusFlowers.crown(level, pos, flower, level.getRandom());
                    return placed;
                }
                state = ChorusFlowers.flower(Direction.UP, 5, level.getRandom());
                boolean placed = original.call(level, pos, state, flags);
                if (placed) ChorusFlowers.crown(level, pos.below(), state, level.getRandom());
                return placed;
            }
            return original.call(level, pos, state, flags);
        }

        @Inject(method = "generatePlant", at = @At("RETURN"))
        private static void naturality$completedConnections(LevelAccessor level, BlockPos target, RandomSource random,
                int spread, CallbackInfo ci) {
            ChorusFlowers.refreshConnections(level, target);
            naturality.blocks.chorus.ChorusFlowers.ChorusSprouts.scatter(level,target,random);
        }
    }

    @Mixin(ChorusPlantBlock.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ChorusPlantMixin extends net.minecraft.world.level.block.Block {
        protected ChorusPlantMixin(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) { super(properties); }

        @Override
        protected net.minecraft.world.level.block.SoundType getSoundType(BlockState state) {
            return net.minecraft.world.level.block.SoundType.STEM;
        }
        @Inject(method = "getStateWithConnections", at = @At("RETURN"), cancellable = true)
        private static void naturality$flowerBases(BlockGetter level, BlockPos pos, BlockState defaultState,
                CallbackInfoReturnable<BlockState> cir) {
            cir.setReturnValue(naturality$connections(level, pos, cir.getReturnValue()));
        }

        private static BlockState naturality$connections(BlockGetter level, BlockPos pos, BlockState state) {
            for (Direction direction : Direction.values()) {
                BlockState neighbor = level.getBlockState(pos.relative(direction));
                if (neighbor.is(net.minecraft.world.level.block.Blocks.CHORUS_FLOWER)) {
                    state = state.setValue(ChorusPlantBlock.PROPERTY_BY_DIRECTION.get(direction),
                        neighbor.getValue(ChorusFlowers.FACING) == direction);
                }
            }
            return state;
        }

        @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true)
        private void naturality$updatedBases(BlockState state, LevelReader level,
                net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                BlockPos neighborPos, BlockState neighborState, net.minecraft.util.RandomSource random,
                CallbackInfoReturnable<BlockState> cir) {
            cir.setReturnValue(ChorusPlantBlock.getStateWithConnections(level, pos, cir.getReturnValue()));
        }

        @Inject(method = "canSurvive", at = @At("RETURN"), cancellable = true)
        private void naturality$directionalRoots(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
            if (!cir.getReturnValue()) cir.setReturnValue(ChorusFlowers.rooted(level, pos));
        }
    }
}
