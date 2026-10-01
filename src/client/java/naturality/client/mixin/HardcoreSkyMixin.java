package naturality.client.mixin;

import naturality.client.lighting.HardcoreDarkness;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class HardcoreSkyMixin {
    @Unique private float naturality$endSkyBrightness = 1.0F;

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void naturality$darkness(ClientLevel level, float partial, Camera camera, SkyRenderState state, CallbackInfo ci) {
        float factor = HardcoreDarkness.sky(level, camera, partial);
        naturality$endSkyBrightness = factor;
        if (state.skybox == DimensionType.Skybox.END) {
            state.endFlashIntensity *= factor;
        }
        if (state.skybox != DimensionType.Skybox.OVERWORLD) return;
        state.skyColor = new Vector3f(state.skyColor).mul(factor);
        state.sunriseAndSunsetColor = new Vector4f(state.sunriseAndSunsetColor).mul(factor, factor, factor, 1.0F);
        // Preserve celestial brightness: rainBrightness controls the sun and moon,
        // while starBrightness controls stars. Only the atmosphere darkens.
    }

    @Redirect(method = "renderEndSky", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
    private com.mojang.renderpearl.api.buffers.GpuBufferSlice naturality$endSky(
            net.minecraft.client.renderer.DynamicGpuData uniforms, org.joml.Matrix4f matrix) {
        float brightness = naturality$endSkyBrightness;
        return uniforms.writeTransform(matrix, new Vector4f(brightness, brightness, brightness, 1.0F));
    }
}

