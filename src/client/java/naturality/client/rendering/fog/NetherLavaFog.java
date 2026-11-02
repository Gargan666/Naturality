package naturality.client.rendering.fog;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import naturality.client.api.rendering.ShaderPacks;
import naturality.config.live.NaturalityConfig;
import naturality.config.startup.FeatureConfig;
import naturality.config.startup.FeatureConfig.Feature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/** Places the surveyed lava atmosphere, clipped by the rendered scene depth. */
@org.jspecify.annotations.NullMarked
public final class NetherLavaFog {
    private static final RenderPipeline PIPELINE = FogPipeline.NetherLava.PIPELINE;
    private static final ByteBuffer data = ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder());
    private static @Nullable MappableRingBuffer uniforms;

    private NetherLavaFog() {}

    public static void initialize() {
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> NetherLavaFogSurvey.reset(null));
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> NetherLavaFogSurvey.invalidate(level, chunk.getPos()));
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> NetherLavaFogSurvey.invalidate(level, chunk.getPos()));
    }

    public static void lakeChanged(ClientLevel level, BlockPos position) {
        NetherLavaFogSurvey.invalidate(level, position);
    }

    /** Shared with terrain's fog marker so immersion/status effects keep their own fog. */
    public static boolean enabled() {
        var client = Minecraft.getInstance();
        var level = client.level;
        return level != null && enabled(level, client.gameRenderer.mainCamera());
    }

    public static boolean enabled(ClientLevel level, Camera camera) {
        return level.dimension().equals(Level.NETHER) && !ShaderPacks.active()
            && FeatureConfig.active(Feature.VISUAL_OVERHAUL) && NaturalityConfig.get().fog.enabled
            && camera.getFluidInCamera() == FogType.NONE
            && (!(camera.entity() instanceof LivingEntity entity)
                || (!entity.hasEffect(MobEffects.BLINDNESS) && !entity.hasEffect(MobEffects.DARKNESS)));
    }

    public static void render(RenderTarget target, @Nullable GpuBufferSlice fog) {
        var client = Minecraft.getInstance();
        var level = client.level;
        NetherLavaFogSurvey.checkWorld(level);
        var camera = client.gameRenderer.mainCamera();
        if (level == null || !enabled(level, camera)) return;
        var color = target.getColorTextureView();
        var depth = target.getDepthTextureView();
        if (color == null || depth == null || fog == null) return;
        var columns = NetherLavaFogSurvey.prepare(level, camera.position(), data);
        if (columns == null) return;
        var ring = uniforms;
        if (ring == null) {
            ring = new MappableRingBuffer(() -> "Naturality Nether lava fog", 130, data.capacity());
            uniforms = ring;
        }
        var uniform = ring.currentBuffer();
        try (var mapped = uniform.map(false, true)) { mapped.data().put(data); }
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Naturality lower lava fog", color, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(
                RenderSystem.getModelViewMatrixCopy(), new Vector4f(1)));
            pass.setUniform("Fog", fog);
            pass.setUniform("LavaFog", uniform);
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            pass.setUniform("Sampler0", depth, sampler);
            pass.setUniform("LavaColumns", columns, sampler);
            pass.draw(3, 1, 0, 0);
        } finally {
            ring.rotate();
        }
    }

    public static void close() {
        if (uniforms != null) { uniforms.close(); uniforms = null; }
        NetherLavaFogSurvey.reset(null);
    }
}
