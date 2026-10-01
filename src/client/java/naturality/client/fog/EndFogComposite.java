package naturality.client.fog;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.FilterMode;
import naturality.Naturality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Vector4f;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/** Blend terrain toward the End sky or a celestial-free Overworld atmosphere. */
public final class EndFogComposite {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Naturality.id("pipeline/end_fog"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Naturality.id("core/end_fog"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
            .withBindGroupLayout(naturality.client.fluid.WaterVisuals.LAYOUT)
            .withBindGroupLayout(naturality.client.fluid.WaterVisuals.SCENE_LAYOUT)
            .withDepthStencilState(Optional.empty())
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build());
    private static @org.jspecify.annotations.Nullable TextureTarget sky;
    private static boolean captured;
    private static final Vector4f background = new Vector4f();

    public static void initialize() {}
    public static void beginFrame(Vector4f color) { captured = false; background.set(color); }

    private static boolean active() {
        var client = Minecraft.getInstance();
        var level = client.level;
        return level != null && EndFogTransparency.isActive(level, client.gameRenderer.mainCamera());
    }

    public static boolean ready() { return captured && active(); }
    public static com.mojang.renderpearl.api.textures.@org.jspecify.annotations.Nullable GpuTextureView atmosphereView() {
        var sky = EndFogComposite.sky;
        return sky != null && ready() ? sky.getColorTextureView() : null;
    }

    public static void captureSky(RenderTarget target) {
        // The Overworld atmosphere was captured separately before this fallback.
        if (!active() || captured) return;
        var sky = prepareTarget(target);
        sky.copyColorFrom(target);
        captured = true;
    }

    private static TextureTarget prepareTarget(RenderTarget target) {
        var sky = EndFogComposite.sky;
        if (sky == null || sky.width != target.width || sky.height != target.height) {
            close();
            sky = new TextureTarget("Naturality fog atmosphere", target.width, target.height,
                GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
            EndFogComposite.sky = sky;
        }
        return sky;
    }

    public static void captureAtmosphere(RenderTarget target, Consumer<RenderPass> draw) {
        var client = Minecraft.getInstance();
        var level = client.level;
        if (!active() || level == null || !level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;
        var sky = prepareTarget(target);
        var color = sky.getColorTexture();
        var depth = sky.getDepthTexture();
        var colorView = sky.getColorTextureView();
        if (color == null || depth == null || colorView == null) return;
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorAndDepthTextures(color, background, depth, 0.0);
        try (var pass = encoder.createRenderPass(() -> "Naturality atmosphere without celestial bodies",
                colorView, Optional.empty(), sky.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            draw.accept(pass);
        }
        captured = true;
    }

    public static void render(RenderTarget target, @org.jspecify.annotations.Nullable GpuBufferSlice fog) {
        var sky = EndFogComposite.sky;
        var level = Minecraft.getInstance().level;
        var colorView = target.getColorTextureView();
        if (!captured || !active() || sky == null || level == null || colorView == null || fog == null) return;
        // Water now samples the Overworld atmosphere in its material draw.
        // A fullscreen overlay here paints over late translucent entities.
        if (level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;
        var transforms = RenderSystem.getDynamicUniforms().writeTransform(
            RenderSystem.getModelViewMatrixCopy(), new Vector4f(1));
        try (var pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Naturality sky edge fog", colorView, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("Fog", fog);
            pass.setUniform("DynamicTransforms", transforms);
            naturality.client.fluid.WaterVisuals.bind(pass);
            naturality.client.fluid.WaterVisuals.bindScene(pass);
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            pass.setUniform("Sampler0", sky.getColorTextureView(), sampler);
            pass.setUniform("Sampler1", target.getDepthTextureView(), sampler);
            pass.draw(3, 1, 0, 0);
        }
    }

    public static void close() {
        captured = false;
        if (sky != null) { sky.destroyBuffers(); sky = null; }
    }
}
