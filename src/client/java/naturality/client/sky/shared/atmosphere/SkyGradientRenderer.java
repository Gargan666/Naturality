package naturality.client.sky.shared.atmosphere;
import naturality.client.sky.overworld.bloodmoon.BloodMoonClient;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/** Keep the biome palette across the whole dome, enriching only its zenith. */
@org.jspecify.annotations.NullMarked
public final class SkyGradientRenderer implements AutoCloseable {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
        .withLocation(Naturality.id("pipeline/sky_gradient"))
        .withVertexShader("core/sky")
        .withFragmentShader(Naturality.id("core/sky_gradient"))
        .withBindGroupLayout(BindGroupLayout.builder().withUniform("SkyPalette", UniformType.UNIFORM_BUFFER).build())
        .withVertexBinding(0, DefaultVertexFormat.POSITION)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_FAN)
        .withColorTargetState(ColorTargetState.DEFAULT).build());
    private final MappableRingBuffer palette = new MappableRingBuffer(() -> "Sky palette", 130, 80);

    public void prepare(Vector3fc middle) {
        palette.rotate();
        var client = Minecraft.getInstance();
        var level = client.level;
        var top = new Vector3f(middle);
        if (level != null) {
            float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            var camera = client.gameRenderer.mainCamera();
            float clear = 1 - Math.clamp(level.getRainLevel(partial) * 4, 0, 1);
            clear = clear * clear * (3 - 2 * clear);
            float angle = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partial);
            float day = Math.clamp(((float)Math.cos(Math.toRadians(angle)) - .05F) * 2, 0, 1);
            float darkness = naturality.client.rendering.lighting.hardcore.HardcoreDarkness.sky(level, camera, partial);
            var vanilla = camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, partial);
            var blue = BloodMoonClient.tint(zenithTint(vanilla).mul(darkness), partial);
            top.lerp(blue, clear * day * .72F);
        }
        try (var mapped = palette.currentBuffer().map(false, true)) {
            Std140Builder.intoBuffer(mapped.data()).putVec4(new Vector4f(top, 1));
            RenderSystem.getModelViewMatrixCopy().invert().get(16, mapped.data());
        }
    }

    public static Vector3f zenithTint(Vector3fc vanilla) {
        float high = Math.max(vanilla.x(), Math.max(vanilla.y(), vanilla.z()));
        float low = Math.min(vanilla.x(), Math.min(vanilla.y(), vanilla.z()));
        float saturation = high > 0 ? (high - low) / high : 0;
        float colorful = Math.clamp(saturation / .35F, 0, 1);
        colorful = colorful * colorful * (3 - 2 * colorful);
        // Neutral biome skies get a modest lift; colored skies retain their hue
        // while gaining the saturated overhead appearance of clear daylight.
        float peak = (high + .06F) * (1 - colorful) + high * .96F * colorful;
        float chroma = 1 + .65F * colorful;
        return new Vector3f(Math.clamp(peak - (high - vanilla.x()) * chroma, 0, 1),
            Math.clamp(peak - (high - vanilla.y()) * chroma, 0, 1),
            Math.clamp(peak - (high - vanilla.z()) * chroma, 0, 1));
    }

    public void render(RenderPass pass, GpuBuffer vertices, Vector3fc middle) {
        pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(
            RenderSystem.getModelViewMatrixCopy(), new Vector4f(middle, 1)));
        pass.setUniform("SkyPalette", palette.currentBuffer());
        pass.setVertexBuffer(0, vertices.slice());
        pass.draw(10, 1, 0, 0);
    }
    public void bindPalette(RenderPass pass) { pass.setUniform("SkyPalette", palette.currentBuffer()); }
    @Override public void close() { palette.close(); }
}
