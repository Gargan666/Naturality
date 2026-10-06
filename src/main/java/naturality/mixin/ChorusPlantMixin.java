package naturality.mixin;

import naturality.chorus.ChorusFlowers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChorusPlantBlock.class)
public abstract class ChorusPlantMixin {
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
