package naturality.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Vector4f;
import java.util.Optional;

/** Purple End atmosphere using vanilla's pixelated sky texture. */
public final class EndSkyRenderer {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder()
        .withLocation(Naturality.id("pipeline/end_sky"))
        .withVertexShader(Naturality.id("core/end_sky"))
        .withFragmentShader(Naturality.id("core/end_sky"))
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withCull(false)
        .withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .build());

    private EndSkyRenderer() {}

    private static final RenderPipeline STARS = RenderPipelines.register(RenderPipeline.builder()
        .withLocation(Naturality.id("pipeline/end_stars"))
        .withVertexShader("core/stars")
        .withFragmentShader(Naturality.id("core/end_stars"))
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withCull(false)
        .withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .build());

    public static void renderStars(RenderPass pass, GpuBuffer stars, int indexCount) {
        var client = Minecraft.getInstance();
        if (client.level == null) return;
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float brightness = naturality.client.lighting.HardcoreDarkness.sky(
            client.level, client.gameRenderer.mainCamera(), partial);
        // One revolution per hour of world time, continuous across the phase wrap.
        double ticks = (client.level.getGameTime() % 72000L) + partial;
        float angle = (float)(ticks * (Math.PI * 2.0 / 72000.0));
        var transform = RenderSystem.getModelViewMatrixCopy().rotateY(angle);
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        pass.setPipeline(RenderSystem.getCompiledPipeline(STARS));
        RenderSystem.bindDefaultUniforms(pass);
        // The shared star shader uses red for X-axis rotation; Y rotation needs zero.
        pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(
            transform, new Vector4f(0, 0, 0, brightness * 0.5F)));
        pass.setVertexBuffer(0, stars.slice());
        pass.setIndexBuffer(indices.getBuffer(indexCount), indices.type());
        pass.drawIndexed(indexCount, 1, 0, 0, 0);
    }

    public static void render(RenderPass pass, GpuBuffer cube,
            net.minecraft.client.renderer.texture.AbstractTexture texture) {
        var client = Minecraft.getInstance();
        if (client.level == null) return;
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float brightness = naturality.client.lighting.HardcoreDarkness.sky(
            client.level, client.gameRenderer.mainCamera(), partial);
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(
            RenderSystem.getModelViewMatrixCopy(), new Vector4f(brightness, brightness, brightness, 1)));
        pass.setUniform("Sampler0", texture.getTextureView(), texture.getSampler());
        pass.setVertexBuffer(0, cube.slice());
        pass.setIndexBuffer(indices.getBuffer(36), indices.type());
        pass.drawIndexed(36, 1, 0, 0, 0);
    }
}
