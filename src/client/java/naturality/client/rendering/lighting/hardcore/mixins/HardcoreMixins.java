package naturality.client.rendering.lighting.hardcore.mixins;

import naturality.client.rendering.lighting.hardcore.HardcoreDarkness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class HardcoreMixins {
    private HardcoreMixins() {}

    @Mixin(LevelRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class HardcoreCloudMixin {
        @ModifyArgs(method = "prepareTranslucents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CloudRenderer;prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V"))
        private void naturality$darkness(Args args) {
            var client = Minecraft.getInstance();
            var level = client.level;
            if (level == null) return;
            float factor = HardcoreDarkness.clouds(level, client.gameRenderer.mainCamera(), args.<Float>get(6));
            int color = args.get(0);
            int red = (int) (((color >>> 16) & 255) * factor);
            int green = (int) (((color >>> 8) & 255) * factor);
            int blue = (int) ((color & 255) * factor);
            args.set(0, naturality.client.systems.event.overworld.bloodmoon.BloodMoonClient.tintArgb(
                (color & 0xff000000) | (red << 16) | (green << 8) | blue, args.<Float>get(6)));
        }
    }

    @Mixin(FogRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class HardcoreFogMixin {
        @Inject(method = "computeFogColor", at = @At("RETURN"))
        private void naturality$darkness(Camera camera, float partial, ClientLevel level, int distance, float darken, Vector4f color, CallbackInfo ci) {
            if (camera.getFluidInCamera() == FogType.LAVA || camera.getFluidInCamera() == FogType.POWDER_SNOW) return;
            float factor = HardcoreDarkness.sky(level, camera, partial);
            // This color also clears the sky and supplies its horizon gradient.
            // Local cave light belongs to world fog only, encoded during upload.
            color.mul(factor, factor, factor, 1.0F);
            if (camera.getFluidInCamera() == FogType.NONE) {
                var red = naturality.client.systems.event.overworld.bloodmoon.BloodMoonClient.tint(new org.joml.Vector3f(color.x, color.y, color.z), partial);
                color.set(red.x, red.y, red.z, color.w);
            }
        }
    }

    @Mixin(LightmapRenderStateExtractor.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class HardcoreLightmapMixin {
        @Inject(method = "extract", at = @At("RETURN"))
        private void naturality$darkness(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
            var client = Minecraft.getInstance();
            var level = client.level;
            if (!state.needsUpdate || level == null) return;
            state.skyLightColor = naturality.client.systems.event.overworld.bloodmoon.BloodMoonClient.lightingTint(state.skyLightColor, partialTicks);
            state.ambientColor = new Vector3f(state.ambientColor).mul(HardcoreDarkness.ambientMultiplier(level));
            state.skyFactor *= HardcoreDarkness.atmosphere(level, client.gameRenderer.mainCamera(), partialTicks);
        }
    }

    @Mixin(SkyRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class HardcoreSkyMixin {
        @Unique private float naturality$endSkyBrightness = 1.0F;

        @Inject(method = "extractRenderState", at = @At("RETURN"))
        private void naturality$darkness(ClientLevel level, float partial, Camera camera, SkyRenderState state, CallbackInfo ci) {
            float factor = HardcoreDarkness.sky(level, camera, partial);
            naturality$endSkyBrightness = factor;
            if (state.skybox == DimensionType.Skybox.END) {
                state.endFlashIntensity *= factor;
            }
            if (state.skybox != DimensionType.Skybox.OVERWORLD) return;
            state.skyColor = naturality.client.systems.event.overworld.bloodmoon.BloodMoonClient.tint(new Vector3f(state.skyColor).mul(factor), partial);
            float dry = 1.0F - level.getRainLevel(partial);
            state.sunriseAndSunsetColor = new Vector4f(state.sunriseAndSunsetColor).mul(factor, factor, factor, dry * dry);
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
}
