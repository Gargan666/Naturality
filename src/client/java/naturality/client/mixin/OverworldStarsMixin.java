package naturality.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;
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
        // Preserve vanilla's star positions and random sequence. The shader expands
        // each repeated center into a quad, so its pulse cannot move the star.
        RandomSource random = RandomSource.createThreadLocalInstance(10842L);
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(1500 * 4 * DefaultVertexFormat.POSITION.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);
            for (int i = 0; i < 1500; i++) {
                float x = random.nextFloat() * 2.0F - 1.0F;
                float y = random.nextFloat() * 2.0F - 1.0F;
                float z = random.nextFloat() * 2.0F - 1.0F;
                random.nextFloat(); // Vanilla size draw.
                float lengthSquared = x * x + y * y + z * z;
                if (lengthSquared <= 0.010000001F || lengthSquared >= 1.0F) continue;
                random.nextDouble(); // Vanilla rotation draw.
                Vector3f center = new Vector3f(x, y, z).normalize(100.0F);
                for (int corner = 0; corner < 4; corner++) builder.addVertex(center);
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                starIndexCount = mesh.drawState().indexCount();
                cir.setReturnValue(RenderSystem.getDevice().createBuffer(() -> "Naturality stars", 40, mesh.vertexBuffer()));
            }
        }
    }
}
