package naturality.mixin;

import naturality.fire.FireGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

/** Attachment is independent of flammability; burning/spreading still uses vanilla odds. */
@Mixin(FireBlock.class)
public abstract class FirePlacementMixin {
    private static boolean naturality$support(BlockGetter level, BlockPos pos) {
        return !FireGeometry.supportBoxes(level, pos).isEmpty();
    }
    private static boolean naturality$attached(BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values())
            if (naturality$support(level, pos.relative(direction))) return true;
        return false;
    }
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void naturality$survive(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (naturality.snow.ShapeRecursionGuard.active()) return;
        if (level.getBlockState(pos.below()).is(Blocks.SNOW)
                || level.getBlockState(pos.below()).is(Blocks.SNOW_BLOCK)) {
            cir.setReturnValue(false);
            return;
        }
        if (naturality.config.GameplaySettings.fireWrapping(level) && naturality$attached(level, pos)) cir.setReturnValue(true);
    }
    @Inject(method = "getStateForPlacement(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("HEAD"), cancellable = true)
    private void naturality$placement(BlockGetter level, BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (naturality.snow.ShapeRecursionGuard.active()) return;
        if (!naturality.config.GameplaySettings.fireWrapping(level)) return;
        BlockState result = ((FireBlock) (Object) this).defaultBlockState();
        if (naturality$support(level, pos.below())) {
            cir.setReturnValue(result);
        } else if (naturality$attached(level, pos)) {
            for (var entry : FireBlock.PROPERTY_BY_DIRECTION.entrySet())
                result = result.setValue(entry.getValue(), naturality$support(level, pos.relative(entry.getKey())));
            cir.setReturnValue(result);
        }
    }
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isFaceSturdy(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z"))
    private boolean naturality$partialSupport(BlockState state, BlockGetter level, BlockPos below, Direction face, Operation<Boolean> original) {
        if (naturality.snow.ShapeRecursionGuard.active()) return original.call(state, level, below, face);
        return original.call(state, level, below, face) || (naturality.config.GameplaySettings.fireWrapping(level) && naturality$attached(level, below.above()));
    }
}
