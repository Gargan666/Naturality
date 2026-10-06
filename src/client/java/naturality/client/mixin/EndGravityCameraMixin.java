package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import naturality.weather.EndGravity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(Camera.class)
public abstract class EndGravityCameraMixin {
    @Shadow private Entity entity;
    @ModifyArg(method="setRotation",at=@At(value="INVOKE",target="Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;"),index=2)
    private float naturality$roll(float roll) { return entity==null?roll:roll+(float)Math.PI*EndGravity.inversion(entity,true); }
    @ModifyExpressionValue(method="alignWithEntity",at=@At(value="INVOKE",target="Lnet/minecraft/util/Mth;lerp(FFF)F"))
    private float naturality$eye(float smoothedHeight) {
        // Transform the interpolated eye height itself. Using the new pose's
        // raw height here makes elytra/crouch changes jump through the ceiling.
        return entity==null?smoothedHeight:EndGravity.eyeHeight(entity,smoothedHeight,true);
    }
}