package naturality.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;
import naturality.client.weather.ParticleWeather;
import naturality.sky.SkyEventType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.joml.Vector4f;
import org.joml.Vector3fc;
import java.util.Optional;

/** A flat paletted celestial sprite, centered opposite the sun. */
public final class RainbowRenderer implements AutoCloseable {
    private static final Identifier PALETTE = Naturality.id("textures/sky/rainbow.png");
    private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder()
        .withLocation(Naturality.id("pipeline/rainbow"))
        .withVertexShader(Naturality.id("core/rainbow"))
        .withFragmentShader(Naturality.id("core/rainbow"))
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withCull(false)
        .withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .build());
    private static ClientLevel world;
    private static float previousStrength, strength;
    private GpuBuffer vertices;
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(RainbowRenderer::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> reset());
    }
    private static void reset() { world = null; previousStrength = strength = 0; }
    private static void tick(Minecraft client) {
        if (world != client.level) { reset(); world = client.level; }
        if (world == null || client.isPaused()) return;
        previousStrength = strength;
        float target = world.dimension().equals(Level.OVERWORLD)
            ? SkyEventsClient.strength(world, SkyEventType.RAINBOW) : 0;
        strength += Math.clamp(target - strength, -.25F, .25F);
    }
    public static float strength() { return strength; }
    public static float easedSize(float strength) {
        float t = Math.clamp(strength / 20F, 0, 1);
        if (t == 0 || t == 1) return t;
        return (float)(t < .5F ? Math.pow(2, 20*t - 10) / 2
            : 1 - Math.pow(2, -20*t + 10) / 2);
    }
    public static float daylightFade(long clockTime) {
        long time = Math.floorMod(clockTime, 24000L);
        if (time >= 13000) return 0;
        if (time < 1000) {
            float t = time / 1000F;
            return t * t * (3 - 2 * t);
        }
        if (time <= 11000) return 1;
        float t = (time - 11000) / 2000F;
        return 1 - t * t * (3 - 2 * t);
    }
    public static float altitudeFade(double cameraY, double cloudTop) {
        if (!Double.isFinite(cloudTop)) return 1;
        float t = (float)Math.clamp((cloudTop - cameraY) / 16.0, 0, 1);
        return t * t * (3 - 2 * t);
    }
    public static float skyBlend(Vector3fc skyColor) {
        float luminance = .2126F * skyColor.x() + .7152F * skyColor.y() + .0722F * skyColor.z();
        float brightness = Math.clamp((luminance - .06F) / .55F, 0, 1);
        return brightness * brightness;
    }
    public void prepare() {
        if (strength <= .001F || world == null || !world.dimension().equals(Level.OVERWORLD)) return;
        Minecraft.getInstance().getTextureManager().getTexture(PALETTE);
        if (vertices != null) return;
        try (var bytes = ByteBufferBuilder.exactlySized(4 * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
            var builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.addVertex(-170, -170, 0).setUv(0, 0);
            builder.addVertex(170, -170, 0).setUv(1, 0);
            builder.addVertex(170, 170, 0).setUv(1, 1);
            builder.addVertex(-170, 170, 0).setUv(0, 1);
            try (var mesh = builder.buildOrThrow()) {
                vertices = RenderSystem.getDevice().createBuffer(() -> "Naturality rainbow ring", 32, mesh.vertexBuffer());
            }
        }
    }
    public void render(RenderPass pass, float sunAngle, float skyBlend) {
        if (vertices == null || world == null || !world.dimension().equals(Level.OVERWORLD) || strength <= .001F) return;
        float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float value = previousStrength + (strength - previousStrength) * partial;
        if (value <= .001F) return;
        float dayFade = daylightFade(world.getOverworldClockTime());
        float cloudFade = altitudeFade(Minecraft.getInstance().gameRenderer.mainCamera().position().y,
            ParticleWeather.highestActiveCloudTop(Minecraft.getInstance()));
        if (dayFade * cloudFade * skyBlend <= .001F) return;
        var texture = Minecraft.getInstance().getTextureManager().getTexture(PALETTE);
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("Sampler0", texture.getTextureView(), texture.getSampler());
        pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(
            RenderSystem.getModelViewMatrixCopy(), new Vector4f(value / 20F, sunAngle,
                dayFade * cloudFade * skyBlend, easedSize(value))));
        pass.setVertexBuffer(0, vertices.slice());
        pass.setIndexBuffer(indices.getBuffer(6), indices.type());
        pass.drawIndexed(6, 1, 0, 0, 0);
    }
    @Override public void close() { if (vertices != null) { vertices.close(); vertices = null; } }
}
