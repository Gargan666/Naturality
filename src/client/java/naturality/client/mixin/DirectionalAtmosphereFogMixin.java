package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import naturality.client.fog.EndFogTransparency;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AtmosphericFogEnvironment.class)
public abstract class DirectionalAtmosphereFogMixin {
    // The sky already draws its sunset in world space. Tinting the entire clear
    // color by camera forwards makes that sky (and its fog capture) change on yaw.
    @ModifyExpressionValue(method = "getBaseColor", at = @At(value = "INVOKE",
        target = "Lorg/joml/Vector3fc;dot(FFF)F"))
    private float naturality$stableHorizon(float facing, ClientLevel level, Camera camera,
            int renderDistance, float partialTicks) {
        return level.dimension().equals(Level.OVERWORLD) && EndFogTransparency.isActive(level, camera)
            ? 0.0F : facing;
    }
}
