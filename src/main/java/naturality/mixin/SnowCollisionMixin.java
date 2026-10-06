package naturality.mixin;

import naturality.snow.SnowGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class SnowCollisionMixin {
    @Inject(method = "isViewBlocking", at = @At("HEAD"), cancellable = true)
    private void naturality$displacedSnowDoesNotBlockCamera(BlockGetter level, BlockPos pos, AABB nearPlane,
            CallbackInfoReturnable<Boolean> cir) {
        if (!naturality.config.GameplaySettings.snowWrapping(level)) return;
        var state = (BlockState)(Object)this;
        if (state.is(Blocks.SNOW) && !SnowGeometry.usesVanillaGeometry(level, pos))
            cir.setReturnValue(false);
    }
    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("RETURN"), cancellable = true)
    private void naturality$cachedSnowCollision(BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        naturality$snowCollision(level,pos,CollisionContext.empty(),cir);
    }
    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("RETURN"), cancellable = true)
    private void naturality$snowCollision(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (!naturality.config.GameplaySettings.snowWrapping(level)) return;
        var state=(BlockState)(Object)this;
        if (state.is(Blocks.SNOW)) { cir.setReturnValue(SnowGeometry.collisionShape(level,pos,state.getValue(SnowLayerBlock.LAYERS))); return; }
        if (state.isAir()) return;
        var result=cir.getReturnValue();
        // Added snow is intersected with the unit cell below. A full collision
        // cube already contains every possible addition, so no search is needed.
        if(net.minecraft.world.level.block.Block.isShapeFullBlock(result))return;
        for (int above=1;above<=SnowGeometry.MAX_DEPTH;above++) {
            var owner=pos.above(above); var snow=level.getBlockState(owner);
            if (!snow.is(Blocks.SNOW)) { if(above>=2 && !SnowGeometry.exposesGround(level,owner)) break; continue; }
            if (snow.getValue(SnowLayerBlock.LAYERS)==1) continue;
            if(SnowGeometry.usesVanillaGeometry(level,owner))continue;
            var displaced=SnowGeometry.collisionShape(level,owner,snow.getValue(SnowLayerBlock.LAYERS)).move(0,above,0);
            // Include only the part that belongs in this traversed collision cell.
            result=Shapes.or(result,Shapes.join(displaced,Shapes.block(),BooleanOp.AND));
        }
        cir.setReturnValue(result);
    }
}



