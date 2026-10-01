package naturality.client.fluid;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.Optional;
import naturality.Naturality;
import naturality.config.NaturalityConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;

/** An immersion tint that preserves darkness as the camera descends. */
public final class WaterImmersionOverlay {
    private static net.minecraft.client.multiplayer.@org.jspecify.annotations.Nullable ClientLevel tintWorld;
    private static long lastTintNanos;
    private static float easedRed, easedGreen, easedBlue;
    private static final RenderPipeline MULTIPLY = pipeline("multiply",
        new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.ZERO, BlendFactor.ZERO, BlendFactor.ONE));
    private static final RenderPipeline ADD = pipeline("add",
        new BlendFunction(BlendFactor.ONE, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE));

    private static RenderPipeline pipeline(String suffix, BlendFunction blend) {
        return RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Naturality.id("pipeline/water_immersion_overlay_" + suffix))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Naturality.id("core/water_immersion_overlay"))
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withDepthStencilState(Optional.empty())
            .withColorTargetState(new ColorTargetState(blend)).build());
    }

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
