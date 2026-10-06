package naturality.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.chorus.ChorusFlowers;
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

@Mixin(ChorusFlowerBlock.class)
public abstract class ChorusFlowerMixin extends Block {
    protected ChorusFlowerMixin(BlockBehaviour.Properties properties) { super(properties); }

    @Inject(method = "createBlockStateDefinition", at = @At("TAIL"))
    private void naturality$properties(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(ChorusFlowers.FACING, ChorusFlowers.BLOOM);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void naturality$defaults(Block plant, BlockBehaviour.Properties properties, CallbackInfo ci) {
        registerDefaultState(defaultBlockState().setValue(ChorusFlowers.FACING, Direction.UP)
            .setValue(ChorusFlowers.BLOOM, false));
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
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
    }
}
