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
    @Shadow private @org.jspecify.annotations.Nullable Entity entity;
    @ModifyArg(method="setRotation",at=@At(value="INVOKE",target="Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;"),index=2)
    private float naturality$roll(float roll) {
        var cameraEntity = entity;
        return cameraEntity==null?roll:roll+(float)Math.PI*EndGravity.inversion(cameraEntity,true);
    }
    @ModifyExpressionValue(method="alignWithEntity",at=@At(value="INVOKE",target="Lnet/minecraft/util/Mth;lerp(FFF)F"))
    private float naturality$eye(float smoothedHeight) {
        // Transform the interpolated eye height itself. Using the new pose's
        // raw height here makes elytra/crouch changes jump through the ceiling.
        var cameraEntity = entity;
        return cameraEntity==null?smoothedHeight:EndGravity.eyeHeight(cameraEntity,smoothedHeight,true);
    }
}
