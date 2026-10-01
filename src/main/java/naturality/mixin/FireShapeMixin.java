package naturality.mixin;

import naturality.fire.FireGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class FireShapeMixin {
    @Inject(method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("HEAD"), cancellable = true)
    private void naturality$outline(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        var state = (BlockState) (Object) this;
        if (naturality.config.GameplaySettings.fireWrapping(level) && state.getBlock() instanceof BaseFireBlock) cir.setReturnValue(FireGeometry.shape(level, pos, state));
    }
    @Inject(method = "getEntityInsideCollisionShape", at = @At("HEAD"), cancellable = true)
    private void naturality$contact(BlockGetter level, BlockPos pos, Entity entity, CallbackInfoReturnable<VoxelShape> cir) {
        var state = (BlockState) (Object) this;
        if (naturality.config.GameplaySettings.fireWrapping(level) && state.getBlock() instanceof BaseFireBlock) cir.setReturnValue(FireGeometry.shape(level, pos, state));
    }
}
