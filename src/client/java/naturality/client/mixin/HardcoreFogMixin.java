package naturality.client.mixin;

import naturality.client.lighting.HardcoreDarkness;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public abstract class HardcoreFogMixin {
    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private void naturality$darkness(Camera camera, float partial, ClientLevel level, int distance, float darken, Vector4f color, CallbackInfo ci) {
        if (camera.getFluidInCamera() == FogType.LAVA || camera.getFluidInCamera() == FogType.POWDER_SNOW) return;
        float factor = HardcoreDarkness.sky(level, camera, partial);
        // This color also clears the sky and supplies its horizon gradient.
        // Local cave light belongs to world fog only, encoded during upload.
        color.mul(factor, factor, factor, 1.0F);
        if (camera.getFluidInCamera() == FogType.NONE) {
            var red = naturality.client.sky.BloodMoonClient.tint(new org.joml.Vector3f(color.x, color.y, color.z), partial);
            color.set(red.x, red.y, red.z, color.w);
        }
    }
}
