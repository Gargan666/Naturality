package naturality.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import naturality.portal.PortalCrossing;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityGetter.class)
public interface PortalEntityCollisionMixin {
    @ModifyExpressionValue(method="getEntityCollisions",at=@At(value="INVOKE",
        target="Lnet/minecraft/world/phys/shapes/Shapes;create(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape naturality$visibleEntity(VoxelShape shape,
        @Local(argsOnly=true) Entity source, @Local(ordinal=1) Entity other) {
        var crossing=PortalCrossing.get(other);
        if(crossing!=null && crossing.valid(other)) shape=crossing.visibleCollision(shape);
        crossing=source==null?null:PortalCrossing.get(source);
        return crossing!=null && crossing.valid(source)?crossing.visibleCollision(shape):shape;
    }
}
