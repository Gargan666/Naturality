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
import naturality.config.live.NaturalityConfig;
import net.minecraft.world.level.material.FogType;

/** Samples a separate scene copy, never the color attachment being written. */
@org.jspecify.annotations.NullMarked
public final class WaterComposite {
    private static final RenderPipeline PIPELINE = WaterShaders.Composite.PIPELINE;
    private static final RenderPipeline MASK = WaterShaders.Composite.MASK;
    private static final RenderPipeline MASK_MULTIDRAW = WaterShaders.Composite.MASK_MULTIDRAW;
    private static final RenderPipeline FINISH = WaterShaders.Composite.FINISH;

    private static @org.jspecify.annotations.Nullable TextureTarget scene;
    private static @org.jspecify.annotations.Nullable TextureTarget mask;
    private static boolean drawingMask;
    private static boolean captured;
    /** Register every water pipeline before the initial shader resource reload. */
    public static void initialize() { }
    public static void beginFrame() { captured = false; }
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
                || !naturality.config.live.NaturalityConfig.get().liquids.waterDepth) return;
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

    /** An immersion tint that preserves darkness as the camera descends. */
    public static final class WaterImmersionOverlay {
        private static final RenderPipeline MULTIPLY = WaterShaders.Immersion.MULTIPLY;
        private static final RenderPipeline ADD = WaterShaders.Immersion.ADD;

        private static net.minecraft.client.multiplayer.@org.jspecify.annotations.Nullable ClientLevel tintWorld;
        private static long lastTintNanos;
        private static float easedRed, easedGreen, easedBlue;
        private WaterImmersionOverlay() {}
        public static void initialize() {}

        public static boolean active() {
            var client = Minecraft.getInstance();
            var level = client.level;
            return level != null && client.player != null
                && client.gameRenderer.mainCamera().getFluidInCamera() == FogType.WATER;
        }

        public static void render() {
            if (!active()) {
                tintWorld = null;
                lastTintNanos = 0;
                return;
            }
            var client = Minecraft.getInstance();
            var level = client.level;
            var player = client.player;
            if (level == null || player == null) return;
            int tint = level.getBiome(player.blockPosition()).value().getWaterColor();
            float red = ((tint >> 16) & 255) / 255.0F;
            float green = ((tint >> 8) & 255) / 255.0F;
            float blue = (tint & 255) / 255.0F;
            long now = System.nanoTime();
            if (tintWorld != level || lastTintNanos == 0) {
                // Entering water starts at the local tint, never a previous world's color.
                easedRed = red;
                easedGreen = green;
                easedBlue = blue;
                tintWorld = level;
            } else {
                // Frame-rate-independent easing, approximately 95% settled in one second.
                float seconds = client.isPaused() ? 0.0F : Math.min((now - lastTintNanos) * 1.0e-9F, 0.1F);
                float blend = (float) -Math.expm1(-seconds / 0.35F);
                easedRed += (red - easedRed) * blend;
                easedGreen += (green - easedGreen) * blend;
                easedBlue += (blue - easedBlue) * blend;
            }
            lastTintNanos = now;
            red = easedRed;
            green = easedGreen;
            blue = easedBlue;
            float depth = Math.clamp((WaterVisuals.cameraDepth() - 2.0F)
                / Math.max(1.0F, NaturalityConfig.get().liquids.waterDarkDepth - 2.0F), 0.0F, 1.0F);
            depth = depth * depth * (3.0F - 2.0F * depth);
            // Exact crossfade: scene * (0.7 + 0.3 * depth * tint) + 0.3 * (1-depth) * tint.
            // At full depth the additive term vanishes, so black stays black.
            float multiply = 0.30F * depth;
            float add = 0.30F * (1.0F - depth);
            var transform = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(),
                new Vector4f(0.70F + multiply * red, 0.70F + multiply * green, 0.70F + multiply * blue, 1));
            var additiveTransform = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(),
                new Vector4f(add * red, add * green, add * blue, 0));
            var colorView = client.gameRenderer.mainRenderTarget().getColorTextureView();
            if (colorView == null) return;
            try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Naturality biome water overlay", colorView, Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(MULTIPLY));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transform);
                pass.draw(3, 1, 0, 0);
                if (add > 0.0F) {
                    pass.setPipeline(RenderSystem.getCompiledPipeline(ADD));
                    pass.setUniform("DynamicTransforms", additiveTransform);
                    pass.draw(3, 1, 0, 0);
                }
            }
        }
    }

    /** Marker applied only to the Sodium renderer supported by our optional mixins. */
    public interface SodiumWaterBridge { }
}
