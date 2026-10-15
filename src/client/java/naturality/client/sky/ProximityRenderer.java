package naturality.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import naturality.Naturality;
import naturality.sky.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.joml.*;
import java.lang.Math;

/** A small celestial sprite with an independent, slight X-axis drift and dusk/dawn fading. */
public final class ProximityRenderer implements AutoCloseable {
    private @org.jspecify.annotations.Nullable GpuBuffer quad;
    private @org.jspecify.annotations.Nullable ClientLevel world;
    private @org.jspecify.annotations.Nullable Identifier texture;
    private int planet;
    private long seed;
    private float opacity;
    private double lastTime = Double.NaN;
    private static int drawn;
    public static int drawn() { return drawn; }
    public static float opacity() { return lastOpacity; }
    private static float lastOpacity;
    public static Vector3f direction(long seed, double time) {
        var random = new java.util.Random(seed);
        double azimuth, height;
        do {
            azimuth = random.nextDouble() * Math.PI * 2;
            height = .25 + random.nextDouble() * .5;
        } while (Math.abs(Math.cos(azimuth) * Math.sqrt(1 - height * height)) < .45);
        double radius = Math.sqrt(1 - height * height);
        return new Vector3f((float)(Math.sin(azimuth) * radius), (float)height, (float)(Math.cos(azimuth) * radius))
            .rotateX((float)(.025 * Math.sin(time * .000002)));
    }
    public static float size(long seed) { return .7F + new java.util.Random(seed ^ 0x5DEECE66DL).nextFloat() * .3F; }
    public void prepare() {
        drawn = 0;
        var client = Minecraft.getInstance();
        var level = client.level;
        if (world != level) { world = level; planet = 0; texture = null; opacity = lastOpacity = 0; lastTime = Double.NaN; }
        if (!naturality.config.Feature.SKY_EVENTS.enabled() || level == null || !level.dimension().equals(Level.OVERWORLD)) return;
        planet = Math.clamp((int)SkyEventsClient.strength(level, SkyEventType.PROXIMITY), 0, 7);
        if (planet == 0) { opacity = lastOpacity = 0; lastTime = Double.NaN; return; }
        long appearance = SkyEventsClient.appearance(level, SkyEventType.PROXIMITY) ^ planet * 341873128712L;
        if (texture == null || seed != appearance) {
            opacity = lastOpacity = 0;
            lastTime = Double.NaN;
        }
        seed = appearance;
        var selectedTexture = Naturality.id("textures/sky/" + ProximityCycle.PLANETS[planet - 1] + ".png");
        texture = selectedTexture;
        client.getTextureManager().getTexture(selectedTexture);
        if (quad != null) return;
        try (var bytes = ByteBufferBuilder.exactlySized(4 * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
            var b = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);
            b.addVertex(-1, -1, 0).setUv(0, 1); b.addVertex(1, -1, 0).setUv(1, 1);
            b.addVertex(1, 1, 0).setUv(1, 0); b.addVertex(-1, 1, 0).setUv(0, 0);
            try (var mesh = b.buildOrThrow()) { quad = RenderSystem.getDevice().createBuffer(() -> "Proximity planet quad", 32, mesh.vertexBuffer()); }
        }
    }
    public void render(RenderPass pass, float rain, float stars) {
        var level = world; var selectedTexture = texture; var vertices = quad;
        if (!naturality.config.Feature.SKY_EVENTS.enabled() || level == null || level != Minecraft.getInstance().level
                || planet == 0 || selectedTexture == null || vertices == null) return;
        var client = Minecraft.getInstance();
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double time = level.getGameTime() + partial;
        double elapsed = Double.isNaN(lastTime) ? 0 : Math.clamp(time - lastTime, 0, 5);
        lastTime = time;
        float target = Math.clamp(stars * 2.5F, 0, 1) * rain;
        opacity += Math.clamp(target - opacity, (float)-elapsed / 40, (float)elapsed / 40);
        lastOpacity = opacity;
        if (opacity <= .0001F) return;
        var normal = direction(seed, level.getGameTime() + partial);
        var right = new Vector3f(normal).cross(0, 1, 0).normalize().mul(size(seed));
        var up = new Vector3f(right).cross(normal).normalize().mul(size(seed));
        var model = new Matrix4f().setColumn(0, new Vector4f(right, 0)).setColumn(1, new Vector4f(up, 0))
            .setColumn(2, new Vector4f(normal, 0)).setColumn(3, new Vector4f(new Vector3f(normal).mul(100), 1));
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        var sprite = client.getTextureManager().getTexture(selectedTexture);
        pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.CELESTIAL));
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("Sampler0", sprite.getTextureView(), sprite.getSampler());
        pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(
            RenderSystem.getModelViewMatrixCopy().mul(model), new Vector4f(1, 1, 1, opacity)));
        pass.setVertexBuffer(0, vertices.slice()); pass.setIndexBuffer(indices.getBuffer(6), indices.type());
        pass.drawIndexed(6, 1, 0, 0, 0); drawn++;
    }
    @Override public void close() { if (quad != null) quad.close(); quad = null; world = null; texture = null; drawn = 0; opacity = lastOpacity = 0; lastTime = Double.NaN; }
}
