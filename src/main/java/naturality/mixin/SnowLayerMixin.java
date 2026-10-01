package naturality.mixin;

import naturality.snow.SnowGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SnowLayerBlock.class)
public abstract class SnowLayerMixin {
    @Inject(method = "canBeReplaced(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/item/context/BlockPlaceContext;)Z", at = @At("HEAD"), cancellable = true)
    private void naturality$replaceEmptyOwner(BlockState state, BlockPlaceContext context, CallbackInfoReturnable<Boolean> cir) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (naturality.config.GameplaySettings.snowWrapping(level) && level.getBlockState(pos) == state
                && SnowGeometry.emptyOwnerCell(level, pos, state.getValue(SnowLayerBlock.LAYERS)))
            cir.setReturnValue(true);
    }
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void naturality$support(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (naturality.snow.NoSnowBlocks.contains(level.getBlockState(pos))
                || naturality.snow.NoSnowBlocks.contains(level.getBlockState(pos.below()))) {
            cir.setReturnValue(false);
            return;
        }
        if (naturality.snow.ShapeRecursionGuard.active()) return;
        if (!naturality.config.GameplaySettings.snowWrapping(level)) return;
        cir.setReturnValue(!SnowGeometry.surfaces(level, pos).isEmpty());
    }
    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void naturality$outline(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (!naturality.config.GameplaySettings.snowWrapping(level)) return;
        cir.setReturnValue(SnowGeometry.shape(level, pos, state.getValue(SnowLayerBlock.LAYERS)));
    }
    @Inject(method = "getVisualShape", at = @At("HEAD"), cancellable = true)
    private void naturality$lightingShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (!naturality.config.GameplaySettings.snowWrapping(level) || SnowGeometry.usesVanillaGeometry(level, pos)) return;
        // The snow mesh may be below this block. Its saved cell must not shade
        // neighboring faces as though the fitted mesh were still here.
        cir.setReturnValue(Shapes.empty());
    }
    @Inject(method = "getShadeBrightness", at = @At("HEAD"), cancellable = true)
    private void naturality$lightingBrightness(BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (naturality.config.GameplaySettings.snowWrapping(level) && !SnowGeometry.usesVanillaGeometry(level, pos))
            cir.setReturnValue(1.0F);
    }
    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void naturality$collision(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (!naturality.config.GameplaySettings.snowWrapping(level)) return;
        cir.setReturnValue(SnowGeometry.collisionShape(level, pos, state.getValue(SnowLayerBlock.LAYERS)));
    }
    @Inject(method = "getBlockSupportShape", at = @At("HEAD"), cancellable = true)
    private void naturality$blockSupport(BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        if (!naturality.config.GameplaySettings.snowWrapping(level)) return;
        cir.setReturnValue(SnowGeometry.shape(level, pos, state.getValue(SnowLayerBlock.LAYERS)));
    }
}

