package naturality.client.fluid;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.Optional;
import naturality.Naturality;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Vector4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.OptionalDouble;

/** Samples a separate scene copy, never the color attachment being written. */
public final class WaterComposite {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Naturality.id("pipeline/water_optics"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Naturality.id("core/water_optics"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
            .withBindGroupLayout(WaterVisuals.LAYOUT)
            .withBindGroupLayout(WaterVisuals.SCENE_LAYOUT)
            .withDepthStencilState(Optional.empty())
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build());
    private static @org.jspecify.annotations.Nullable TextureTarget scene;
    private static @org.jspecify.annotations.Nullable TextureTarget mask;
    private static boolean drawingMask;
    private static boolean captured;
    private static final RenderPipeline MASK = maskPipeline(false);
    private static final RenderPipeline MASK_MULTIDRAW = maskPipeline(true);
    private static final RenderPipeline FINISH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Naturality.id("pipeline/water_finish"))
            .withVertexShader("core/screenquad").withFragmentShader(Naturality.id("core/water_finish"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
            .withBindGroupLayout(WaterVisuals.LAYOUT).withBindGroupLayout(WaterVisuals.SCENE_LAYOUT)
            .withDepthStencilState(Optional.empty()).withColorTargetState(ColorTargetState.DEFAULT).build());

    private static RenderPipeline maskPipeline(boolean multidraw) {
        return RenderPipelines.register(RenderPipeline.builder(multidraw ? RenderPipelines.MULTIDRAW_TERRAIN_SNIPPET : RenderPipelines.TERRAIN_SNIPPET)
            .withLocation(Naturality.id(multidraw ? "pipeline/water_mask_multidraw" : "pipeline/water_mask"))
            .withShaderDefine("NATURALITY_WATER_MASK")
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA32_FLOAT, 15)).build());
    }

    public static void beginFrame() { captured = false; }
    public static @org.jspecify.annotations.Nullable GpuTextureView maskView() { return captured && !drawingMask && mask != null ? mask.getColorTextureView() : null; }
    public static @org.jspecify.annotations.Nullable GpuTextureView depthView() { return captured && scene != null ? scene.getDepthTextureView() : null; }

    public static void render(RenderTarget target, ChunkSectionsToRender chunks) {
        var colorView = target.getColorTextureView();
        if (!WaterVisuals.ready() || colorView == null) return;
        var scene = WaterComposite.scene;
        var mask = WaterComposite.mask;
        if (mask == null || scene == null || scene.width != target.width || scene.height != target.height) {
            close();
            scene = new TextureTarget("Naturality water scene", target.width, target.height,
                GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
            mask = new TextureTarget("Naturality visible water surface", target.width, target.height,
                GpuFormat.RGBA32_FLOAT, GpuFormat.D32_FLOAT);
        }
        WaterComposite.scene = scene;
        WaterComposite.mask = mask;
        var maskColor = mask.getColorTextureView();
        if (maskColor == null) return;
        scene.copyColorFrom(target);
        scene.copyDepthFrom(target);
        mask.copyDepthFrom(target);
        captured = true;
        var client = Minecraft.getInstance();
        drawingMask = true;
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Naturality water surface depth mask", maskColor, Optional.of(new Vector4f()),
                mask.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            ((naturality.client.mixin.WaterLayersAccess) chunks).naturality$renderWaterMask(
                ChunkSectionLayerGroup.TRANSLUCENT.layers(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST), pass,
                client.getTextureManager().getTexture(naturality.client.AtlasLocations.BLOCKS).getTextureView(),
                client.gameRenderer.lightmap(), MASK, MASK_MULTIDRAW);
        } finally { drawingMask = false; }
        var transform = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(1));
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Naturality directional water fog and pixel refraction", colorView, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transform);
            WaterVisuals.bind(pass);
            WaterVisuals.bindScene(pass);
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            pass.setUniform("Sampler0", scene.getColorTextureView(), sampler);
            pass.setUniform("Sampler1", target.getDepthTextureView(), sampler);
            pass.draw(3, 1, 0, 0);
        }
        fadeOpaqueBackground(target);
    }

    // Run before translucent entities and water: opaque depth cannot describe
    // those later draws, so it must never darken their already-composited color.
    private static void fadeOpaqueBackground(RenderTarget target) {
        var scene = WaterComposite.scene;
        var colorView = target.getColorTextureView();
        if (scene == null || colorView == null) return;
        if (!captured || !WaterVisuals.ready()
                || Minecraft.getInstance().gameRenderer.mainCamera().getFluidInCamera() != net.minecraft.world.level.material.FogType.WATER
                || !naturality.config.NaturalityConfig.get().liquids.waterDepth) return;
        scene.copyColorFrom(target);
        var transform = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(1));
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Naturality unified underwater distance fade", colorView, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(FINISH));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transform);
            WaterVisuals.bind(pass);
            WaterVisuals.bindScene(pass);
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            pass.setUniform("Sampler0", scene.getColorTextureView(), sampler);
            pass.setUniform("Sampler1", scene.getDepthTextureView(), sampler);
            pass.draw(3, 1, 0, 0);
        }
    }
    public static void close() {
        captured = false;
        if (scene != null) { scene.destroyBuffers(); scene = null; }
        if (mask != null) { mask.destroyBuffers(); mask = null; }
    }
}
