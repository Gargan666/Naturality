package naturality.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.*;
import java.util.Optional;
import naturality.Naturality;
import naturality.client.mixin.SunSpriteAccess;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;
import org.joml.Vector4f;

/** Sun-aligned pixel grid; the shader rasterizes eight animated rectangular beams. */
public final class SunBeams implements AutoCloseable {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder()
        .withLocation(Naturality.id("pipeline/sun_beams"))
        .withVertexShader(Naturality.id("core/sun_beams"))
        .withFragmentShader(Naturality.id("core/sun_beams"))
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withCull(false)
        .withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(BlendFunction.OVERLAY))
        .build());
    private @org.jspecify.annotations.Nullable GpuBuffer vertices;
    private @org.jspecify.annotations.Nullable TextureAtlasSprite sampledSprite;
    private int color = 0xFFFFDA70;

    private void rebuildGrid(TextureAtlasSprite sprite) {
        if (vertices != null) vertices.close();
        // Vanilla maps the full sun texture to a 60-by-60 celestial plane.
        float pixelX = 60F / sprite.contents().width();
        float pixelY = 60F / sprite.contents().height();
        float extent = 56 + Math.max(pixelX, pixelY);
        try (var bytes = ByteBufferBuilder.exactlySized(4 * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
            var builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.addVertex(-extent, 0, -extent).setUv(pixelX, pixelY);
            builder.addVertex(extent, 0, -extent).setUv(pixelX, pixelY);
            builder.addVertex(extent, 0, extent).setUv(pixelX, pixelY);
            builder.addVertex(-extent, 0, extent).setUv(pixelX, pixelY);
            try (var mesh = builder.buildOrThrow()) {
                vertices = RenderSystem.getDevice().createBuffer(() -> "Naturality sun beams", 32, mesh.vertexBuffer());
            }
        }
    }

    public static float opacity(float sunAngle) {
        float height = Math.max(0, (float) Math.cos(sunAngle));
        float ease = height * height * (3 - 2 * height);
        return 0.6F - 0.5F * ease;
    }

    public void render(RenderPass pass, PoseStack pose, float angle, float rain, TextureAtlasSprite sprite) {
        if (rain <= 0) return;
        if (sampledSprite != sprite) {
            sampledSprite = sprite;
            rebuildGrid(sprite);
            var image = ((SunSpriteAccess) sprite.contents()).naturality$sunImage();
            // The yellow border is the most yellow opaque color, excluding the white center
            // and transparent padding. Read the actual atlas source, including resource packs.
            int best = -1;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixel(x, y);
                if (ARGB.alpha(pixel) < 250) continue;
                int score = Math.min(ARGB.red(pixel), ARGB.green(pixel)) - ARGB.blue(pixel);
                if (score > best) { best = score; color = pixel; }
            }
        }
        var transform = RenderSystem.getModelViewMatrixCopy().mul(pose.last().pose()).translate(0, 100, 0);
        // The sun center is now in view space; the camera looks along negative Z.
        // Using the render transform keeps this aligned with the displayed sun in
        // first/third person, including interpolated camera rotation and view effects.
        float distance = (float) Math.sqrt(transform.m30() * transform.m30()
            + transform.m31() * transform.m31() + transform.m32() * transform.m32());
        float alignment = distance > 0 ? -transform.m32() / distance : 0;
        float focus = Math.clamp((alignment - 0.70710678F) / (1 - 0.70710678F), 0, 1);
        float viewOpacity = 0.3F + 0.7F * focus * focus * (3 - 2 * focus);
        var tint = new Vector4f(ARGB.red(color) / 255F, ARGB.green(color) / 255F,
            ARGB.blue(color) / 255F, opacity(angle) * rain * viewOpacity);
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(transform, tint));
        var vertices = this.vertices;
        if (vertices == null) return;
        pass.setVertexBuffer(0, vertices.slice());
        pass.setIndexBuffer(indices.getBuffer(6), indices.type());
        pass.drawIndexed(6, 1, 0, 0, 0);
    }

    @Override public void close() { if (vertices != null) vertices.close(); }
}
