package naturality.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import naturality.portal.PortalCrossing;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.phys.shapes.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockCollisions.class)
public abstract class PortalCollisionMixin {
    @SuppressWarnings("null") @Shadow @Final private CollisionContext context;
    @SuppressWarnings("null") @Shadow @Final private BlockPos.MutableBlockPos pos;
    @ModifyExpressionValue(method="computeNext",at=@At(value="INVOKE",
        target="Lnet/minecraft/world/phys/shapes/CollisionContext;getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape naturality$visibleCollision(VoxelShape shape) {
        if(!(context instanceof EntityCollisionContext ec)) return shape;
        var entity=ec.getEntity();
        if (entity == null) return shape;
        var crossing=PortalCrossing.get(entity);
        return crossing==null || !crossing.valid(entity)?shape:
            crossing.visibleCollision(shape.move(pos)).move(-pos.getX(),-pos.getY(),-pos.getZ());
    }
}
