package naturality.mixin;

import naturality.snow.SnowGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(BlockGetter.class)
public interface SnowPickingMixin {
    @WrapMethod(method="clip")
    private BlockHitResult naturality$snowContext(ClipContext context, Operation<BlockHitResult> original) {
        if (!naturality.config.GameplaySettings.snowWrapping((BlockGetter)this)) return original.call(context);
        var previous=SnowGeometry.PICK.get(); SnowGeometry.PICK.set(context);
        try { return original.call(context); }
        finally { if(previous==null) SnowGeometry.PICK.remove(); else SnowGeometry.PICK.set(previous); }
    }
    @Inject(method="clipWithInteractionOverride",at=@At("RETURN"),cancellable=true)
    private void naturality$snowHit(Vec3 from, Vec3 to, BlockPos pos, VoxelShape shape, BlockState state,
            CallbackInfoReturnable<BlockHitResult> cir) {
        if (!naturality.config.GameplaySettings.snowWrapping((BlockGetter)this)) return;
        var context=SnowGeometry.PICK.get(); if(context==null)return;
        BlockGetter level=(BlockGetter)this;
        var best=cir.getReturnValue(); double distance=best==null?Double.POSITIVE_INFINITY:best.getLocation().distanceToSqr(from);
        for(int offset=1;offset<=SnowGeometry.MAX_DEPTH;offset++) {
            var owner=pos.above(offset);var snow=level.getBlockState(owner);if(!snow.is(Blocks.SNOW)) { if(offset>=2 && !SnowGeometry.exposesGround(level,owner)) break; continue; }
            var hit=context.getBlockShape(snow,level,owner).clip(from,to,owner);
            if(hit!=null && new AABB(pos).inflate(1e-6).contains(hit.getLocation()) && hit.getLocation().distanceToSqr(from)<distance) {
                best=hit;distance=hit.getLocation().distanceToSqr(from);
            }
        }
        if (best != cir.getReturnValue()) cir.setReturnValue(best);
    }
}


