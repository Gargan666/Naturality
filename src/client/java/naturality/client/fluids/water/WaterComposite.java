package naturality.client.fluids.water;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.Optional;
import org.joml.Vector4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.OptionalDouble;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

/** Samples a separate scene copy, never the color attachment being written. */
@org.jspecify.annotations.NullMarked
public final class WaterComposite {
    private static final RenderPipeline PIPELINE = WaterShaders.Composite.PIPELINE;
    private static final RenderPipeline MASK = WaterShaders.Composite.MASK;
    private static final RenderPipeline MASK_MULTIDRAW = WaterShaders.Composite.MASK_MULTIDRAW;
    private static final RenderPipeline SURFACE = WaterShaders.Composite.SURFACE;

    private static @org.jspecify.annotations.Nullable TextureTarget scene;
    private static @org.jspecify.annotations.Nullable TextureTarget mask;
    private static boolean drawingMask;
    private static boolean captured;
    /** Register every water pipeline before the initial shader resource reload. */
    public static void initialize() { }
    public static void beginFrame() { captured = false; WaterVisuals.SunTransmission.beginFrame(); }
    public static boolean drawingMask() { return drawingMask; }
    public static @org.jspecify.annotations.Nullable GpuTextureView maskView() { return captured && !drawingMask && mask != null ? mask.getColorTextureView() : null; }
    public static @org.jspecify.annotations.Nullable GpuTextureView depthView() { return captured && scene != null ? scene.getDepthTextureView() : null; }

    public static void render(RenderTarget target, ChunkSectionsToRender chunks) {
        // Replacement terrain renderers may provide no vanilla terrain uniforms.
        // Invalidate last frame's mask before skipping: it must not be sampled later.
        captured = false;
        boolean sodium = chunks instanceof naturality.client.fluids.water.WaterComposite.SodiumWaterBridge;
        if (!sodium && ((naturality.client.fluids.water.mixins.WaterMixins.WaterLayersAccess) chunks).naturality$terrainTransform() == null) return;
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
        var client = Minecraft.getInstance();
        drawingMask = true;
        // Alpha marks actual water coverage. JOML's no-argument Vector4f has
        // w=1, which would mark untouched sky pixels as water as well.
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Naturality water surface depth mask", maskColor, Optional.of(new Vector4f(0.0F)),
                mask.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            if (sodium) {
                chunks.renderGroup(ChunkSectionLayerGroup.TRANSLUCENT, pass,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),
                    client.getTextureManager().getTexture(naturality.client.api.rendering.AtlasLocations.BLOCKS).getTextureView(), false);
            } else ((naturality.client.fluids.water.mixins.WaterMixins.WaterLayersAccess) chunks).naturality$renderWaterMask(
                ChunkSectionLayerGroup.TRANSLUCENT.layers(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST), pass,
                client.getTextureManager().getTexture(naturality.client.api.rendering.AtlasLocations.BLOCKS).getTextureView(),
                client.gameRenderer.lightmap(), MASK, MASK_MULTIDRAW);
        } finally { drawingMask = false; }
        captured = true;
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
    }

    /** Refract the completed sky/cloud image after transparency, before outlines and HUD. */
    public static void renderSurface(RenderTarget target) {
        var scene = WaterComposite.scene;
        var colorView = target.getColorTextureView();
        var config = naturality.config.live.NaturalityConfig.get().liquids;
        if (!captured || scene == null || colorView == null || !WaterVisuals.ready()
                || !config.waterDepth
                || Minecraft.getInstance().gameRenderer.mainCamera().getFluidInCamera()
                    != net.minecraft.world.level.material.FogType.WATER) return;
        scene.copyColorFrom(target);
        scene.copyDepthFrom(target);
        WaterVisuals.SunTransmission.prepare();
        var transform = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(1));
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Naturality underwater surface transmission", colorView, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(SURFACE));
            WaterVisuals.SunTransmission.bind(pass);
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

    /** Marker applied only to the Sodium renderer supported by our optional mixins. */
    public interface SodiumWaterBridge { }
}
