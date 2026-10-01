package naturality.client.glint;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.*;
import naturality.Naturality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;

/** Capture the actual submitted surfaces, then outline only their visible silhouette. */
public final class GlintContours {
    private static final RenderPipeline MASK = RenderPipelines.register(RenderPipeline.builder()
        .withLocation(Naturality.id("pipeline/glint_contour_mask"))
        .withVertexShader(Naturality.id("core/glint_contour_mask"))
        .withFragmentShader(Naturality.id("core/glint_contour_mask"))
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.FOG)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
        .withPrimitiveTopology(PrimitiveTopology.QUADS).withCull(false)
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
        .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA32_FLOAT, 15)).build());
    private static RenderPipeline expansion(boolean gui) {
        var builder = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Naturality.id(gui ? "pipeline/glint_contour_gui" : "pipeline/glint_contour"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Naturality.id("core/glint_contour"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2)
            .withDepthStencilState(Optional.empty());
        builder.withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withColorTargetState(new ColorTargetState(gui
                ? new BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA)
                : BlendFunction.GLINT));
        return RenderPipelines.register(builder.build());
    }
    private static final RenderPipeline EDGE = expansion(false);
    private static final RenderPipeline GUI_EDGE = expansion(true);
    private static float previewScale;
    private static boolean handFrame;
    private record Key(Identifier texture, boolean armor, boolean procedural) { }
    private static final Map<Key, Batch> batches = new LinkedHashMap<>();
    private static @org.jspecify.annotations.Nullable TextureTarget worldMask, previewMask;
    private static boolean capturing;
    public static int lastItemDraws;
    public static int lastArmorDraws;

    public static void beginFrame() {
        endFrame();
        lastItemDraws = lastArmorDraws = 0;
        previewScale = 0;
        handFrame = false;
        capturing = naturality.config.NaturalityConfig.get().effects.worldGlintContours;
    }

    public static void beginHandFrame() {
        endFrame();
        previewScale = 0;
        handFrame = true;
        capturing = naturality.config.NaturalityConfig.get().effects.handGlintContours;
    }

    public static void beginPreview(float scale) {
        beginFrame();
        previewScale = scale;
        handFrame = false;
        capturing = naturality.config.NaturalityConfig.get().effects.previewGlintContours;
    }

    /** Non-owning adapter for the inventory portrait framebuffer. */
    public static void renderPreview(@org.jspecify.annotations.Nullable GpuTexture color, @org.jspecify.annotations.Nullable GpuTextureView colorView,
            @org.jspecify.annotations.Nullable GpuTexture depth, @org.jspecify.annotations.Nullable GpuTextureView depthView) {
        if (color == null || colorView == null || depth == null || depthView == null) return;
        render(new RenderTarget("Naturality borrowed preview", color.getFormat(), depth.getFormat()) {{
            width = color.getWidth(0); height = color.getHeight(0);
            colorTexture = color; colorTextureView = colorView;
            depthTexture = depth; depthTextureView = depthView;
        }}, null);
    }

    public static VertexConsumer capture(VertexConsumer original, @org.jspecify.annotations.Nullable Identifier texture, boolean armor) {
        if (!capturing || texture == null) return original;
        var batch = batches.computeIfAbsent(new Key(texture, armor, true), _ -> new Batch());
        return new Copy(original, batch.builder);
    }

    public static @org.jspecify.annotations.Nullable VertexConsumer armor(@org.jspecify.annotations.Nullable Identifier texture, boolean procedural) {
        if (!capturing || texture == null) return null;
        return batches.computeIfAbsent(new Key(texture, true, procedural), _ -> new Batch()).builder;
    }

    private static final class Batch implements AutoCloseable {
        final ByteBufferBuilder bytes = new ByteBufferBuilder(4096);
        final BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);
        @Override public void close() { bytes.close(); }
    }

    public static void render(RenderTarget target, @org.jspecify.annotations.Nullable GpuBufferSlice fog) {
        capturing = false;
        if (batches.isEmpty()) return;
        try {
            var mask = previewScale > 0 ? previewMask : worldMask;
            var colorView = target.getColorTextureView();
            if (colorView == null) return;
            if (mask == null || mask.width != target.width || mask.height != target.height) {
                if (mask != null) mask.destroyBuffers();
                mask = new TextureTarget("Naturality enchanted silhouettes", target.width, target.height,
                    GpuFormat.RGBA32_FLOAT, GpuFormat.D32_FLOAT);
            }
            if (previewScale > 0) { previewMask = mask; }
            else { worldMask = mask; }
            var maskColor = mask.getColorTextureView();
            if (maskColor == null) return;
            // Alpha zero means empty background. JOML's no-arg Vector4f has w=1,
            // which would make every pixel occupied and suppress every contour.
            mask.copyDepthFrom(target);
            var textures = Minecraft.getInstance().getTextureManager();
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Naturality enchanted silhouette mask", maskColor, Optional.of(new Vector4f(0, 0, 0, 0)),
                    mask.getDepthTextureView(), OptionalDouble.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(MASK));
                RenderSystem.bindDefaultUniforms(pass);
                if (fog != null) pass.setUniform("Fog", fog);
                for (var entry : batches.entrySet()) {
                    try (var mesh = entry.getValue().builder.build()) {
                        if (mesh == null) continue;
                        var key = entry.getKey();
                        var modelView = RenderSystem.getModelViewMatrixCopy();
                        // Match the armor render type's view-depth offset; otherwise
                        // the replayed armor is behind its own scene-depth surface.
                        if (key.armor) RenderSystem.getProjectionType().applyLayeringTransform(modelView, 1.0F);
                        pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(
                            modelView, new Vector4f(previewScale, handFrame ? 1 : 0, 1, 1)));
                        var palette = key.armor ? ItemFeatureRenderer.ENCHANTED_GLINT_ARMOR : ItemFeatureRenderer.ENCHANTED_GLINT_ITEM;
                        pass.setUniform("Sampler0", textures.getTexture(key.texture).getTextureView(), sampler);
                        pass.setUniform("Sampler1", textures.getTexture(palette).getTextureView(), sampler);
                        try (var vertices = RenderSystem.getDevice().createBuffer(() -> "Naturality contour vertices", 32, mesh.vertexBuffer())) {
                            int count = mesh.drawState().indexCount();
                            var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                            pass.setVertexBuffer(0, vertices.slice());
                            pass.setIndexBuffer(indices.getBuffer(count), indices.type());
                            pass.drawIndexed(count, 1, 0, 0, 0);
                            if (key.armor) lastArmorDraws++; else lastItemDraws++;
                        }
                    }
                }
            }
            try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Naturality enchanted contours", colorView, Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(previewScale > 0 ? GUI_EDGE : EDGE));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(
                    RenderSystem.getModelViewMatrixCopy(), new Vector4f(previewScale, handFrame ? 1 : 0, 1, 1)));
                pass.setUniform("Sampler0", mask.getColorTextureView(), sampler);
                pass.setUniform("Sampler1", mask.getColorTextureView(), sampler);
                pass.setUniform("Sampler2", target.getDepthTextureView(), sampler);
                pass.draw(3, 1, 0, 0);
            }
        } finally { endFrame(); }
    }

    public static void endFrame() {
        capturing = false;
        batches.values().forEach(Batch::close);
        batches.clear();
    }

    public static void close() {
        endFrame();
        for (var target : new TextureTarget[] {worldMask, previewMask})
            if (target != null) target.destroyBuffers();
        worldMask = previewMask = null;
    }

    /** Preserve the original vertex stream, copying only position and base UV. */
    private record Copy(VertexConsumer original, VertexConsumer copy) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { original.addVertex(x,y,z); copy.addVertex(x,y,z); return this; }
        @Override public VertexConsumer setUv(float u, float v) { original.setUv(u,v); copy.setUv(u,v); return this; }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) { original.setColor(r,g,b,a); return this; }
        @Override public VertexConsumer setColor(int color) { original.setColor(color); return this; }
        @Override public VertexConsumer setUv1(int u,int v) { original.setUv1(u,v); return this; }
        @Override public VertexConsumer setUv2(int u,int v) { original.setUv2(u,v); return this; }
        @Override public VertexConsumer setUv3(float u,float v) { original.setUv3(u,v); return this; }
        @Override public VertexConsumer setNormal(float x,float y,float z) { original.setNormal(x,y,z); return this; }
        @Override public VertexConsumer setLineWidth(float width) { original.setLineWidth(width); return this; }
    }
    private GlintContours() { }
}




