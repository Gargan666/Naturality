package naturality.mixin;

import naturality.fire.FireGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockGetter.class)
public interface FirePickingMixin {
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "clip")
    private BlockHitResult naturality$pickContext(net.minecraft.world.level.ClipContext context,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<BlockHitResult> original) {
        if (!naturality.config.GameplaySettings.fireWrapping((BlockGetter) this)) return original.call(context);
        var previous = FireGeometry.PICK_CONTEXT.get();
        FireGeometry.PICK_CONTEXT.set(context);
        try { return original.call(context); }
        finally {
            if (previous == null) FireGeometry.PICK_CONTEXT.remove();
            else FireGeometry.PICK_CONTEXT.set(previous);
        }
    }
    @Inject(method = "clipWithInteractionOverride", at = @At("RETURN"), cancellable = true)
    private void naturality$neighborFire(Vec3 from, Vec3 to, BlockPos pos, VoxelShape originalShape,
            BlockState state, CallbackInfoReturnable<BlockHitResult> cir) {
        BlockGetter level = (BlockGetter) this;
        if (!naturality.config.GameplaySettings.fireWrapping(level)) return;
        var context = FireGeometry.PICK_CONTEXT.get();
        if (context == null) return;
        BlockHitResult closest = cir.getReturnValue();
        double distance = closest == null ? Double.POSITIVE_INFINITY : closest.getLocation().distanceToSqr(from);
        for (BlockPos candidate : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (candidate.equals(pos)) continue;
            var fire = level.getBlockState(candidate);
            if (!(fire.getBlock() instanceof BaseFireBlock)) continue;
            // Collision/visibility rays must still pass through non-solid fire.
            if (context.getBlockShape(fire, level, candidate).isEmpty()) continue;
            var hit = FireGeometry.shape(level, candidate, fire).clip(from, to, candidate.immutable());
            // Only return intersections in the cell currently traversed. Returning
            // a farther neighbor hit here could otherwise skip a wall on the ray.
            if (hit != null && new net.minecraft.world.phys.AABB(pos).inflate(1e-6).contains(hit.getLocation())
                    && hit.getLocation().distanceToSqr(from) < distance) {
                closest = hit; distance = hit.getLocation().distanceToSqr(from);
            }
        }
        cir.setReturnValue(closest);
    }
}
