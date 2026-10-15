package naturality.client.sky.overworld.stars.mixins;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.DynamicGpuData;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SkyRenderer.class)
@org.jspecify.annotations.NullMarked
public abstract class OverworldStarsMixin {
    @Shadow private int starIndexCount;
    @Unique private float naturality$starAngle;

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"))
    private void naturality$captureStarAngle(com.mojang.renderpearl.api.commands.RenderPass pass,
            com.mojang.blaze3d.vertex.PoseStack pose, float sunAngle, float moonAngle, float starAngle,
            net.minecraft.world.level.MoonPhase moonPhase, float rainBrightness, float starBrightness,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        naturality$starAngle = starAngle;
    }

    @Redirect(method = "renderStars", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
    private GpuBufferSlice naturality$passStarAngle(DynamicGpuData uniforms, Matrix4f transform, Vector4f color) {
        // The stars shader does not use ColorModulator RGB for color; reserve R
        // for the sphere's rotation angle and retain vanilla brightness in alpha.
        return uniforms.writeTransform(transform, new Vector4f(naturality$starAngle, 0.0F, 0.0F, color.w));
    }

    @Inject(method = "buildStars", at = @At("HEAD"), cancellable = true)
    private void naturality$starCenters(CallbackInfoReturnable<GpuBuffer> cir) {
        var stars = naturality.client.sky.overworld.stars.StarGeometry.create();
        starIndexCount = stars.indexCount();
        cir.setReturnValue(stars.buffer());
    }
}
