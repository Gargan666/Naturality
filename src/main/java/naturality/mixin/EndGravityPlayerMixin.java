package naturality.mixin;

import naturality.weather.EndGravity;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class EndGravityPlayerMixin {
    @WrapMethod(method="maybeBackOffFromEdge")
    private Vec3 naturality$ceilingEdge(Vec3 delta,
            MoverType type,
            Operation<Vec3> original) {
        if(!EndGravity.inverted((Player)(Object)this))return original.call(delta,type);
        var clipped=original.call(new Vec3(delta.x,-delta.y,delta.z),type);
        return new Vec3(clipped.x,-clipped.y,clipped.z);
    }
    @ModifyArg(method="canFallAtLeast",at=@At(value="INVOKE",
        target="Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"),index=1)
    private AABB naturality$ceilingEdgeSupport(AABB area) {
        var p=(Player)(Object)this;
        if(!EndGravity.inverted(p))return area;
        double sum=p.getBoundingBox().minY+p.getBoundingBox().maxY;
        return new AABB(area.minX,sum-area.maxY,area.minZ,area.maxX,sum-area.minY,area.maxZ);
    }
    @Inject(method="canPlayerFitWithinBlocksAndEntitiesWhen",at=@At("HEAD"),cancellable=true)
    private void naturality$ceilingPoseFits(Pose pose, CallbackInfoReturnable<Boolean> cir) {
        var player=(Player)(Object)this;
        if(!EndGravity.inverted(player))return;
        var dimensions=player.getDimensions(pose);
        // The feet are on the upper face of the box. Grow toward the head,
        // below the ceiling, rather than into the supporting block.
        var box=dimensions.makeBoundingBox(player.getX(),
            player.getBoundingBox().maxY-dimensions.height(),player.getZ());
        cir.setReturnValue(player.level().noCollision(player,box.deflate(1.0e-7)));
    }
}
