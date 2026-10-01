package naturality.client.sky;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.*;
import java.util.*;
import naturality.Naturality;
import naturality.config.NaturalityConfig;
import naturality.sky.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;

/** Emissive world-space curtains, drawn after terrain fog and before the cloud overlay. */
public final class AuroraRenderer {
    private static final Identifier PALETTE = Naturality.id("textures/sky/aurora.png");
    private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder()
        .withLocation(Naturality.id("pipeline/aurora"))
        .withVertexShader(Naturality.id("core/aurora"))
        .withFragmentShader(Naturality.id("core/aurora"))
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS).withCull(false)
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        // Luminous curtains add through one another without depending on draw order.
        .withColorTargetState(new ColorTargetState(BlendFunction.OVERLAY)).build());
    private record Key(int x, int z) {}
    private record Tile(GpuBuffer vertices, int indices) {}
    private static final Map<Key, Tile> TILES = new HashMap<>();
    private static @org.jspecify.annotations.Nullable ClientLevel world;
    private static float previousStrength, strength;
    private static float motionPhase;
    private static int drawnPanels;
    private static int tileSegments = -1;
    private AuroraRenderer() {}
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(AuroraRenderer::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> close());
        ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> close());
    }
    public static float strength() { return strength; }
    public static int drawnPanels() { return drawnPanels; }
    private static void tick(Minecraft client) {
        if (world != client.level) { close(); world = client.level; }
        var world = AuroraRenderer.world;
        if (world == null || client.isPaused()) return;
        previousStrength = strength;
        float target = world.dimension().equals(Level.OVERWORLD)
            ? SkyEventsClient.strength(world, SkyEventType.AURORA_BOREALIS) : 0;
        strength += Math.clamp(target - strength, -.25F, .25F);
        motionPhase += .05F * (.18F + .82F * strength / 20F);
    }
    private static Tile build(Key key) {
        var panels = AuroraGeometry.tile(key.x, key.z, tileSegments);
        try (var bytes = ByteBufferBuilder.exactlySized(panels.size() * 4 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())) {
            var builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (var p : panels) {
                vertex(builder, p.a(), p.topA(), p, false);
                vertex(builder, p.b(), p.topB(), p, false);
                vertex(builder, p.b(), 0, p, true);
                vertex(builder, p.a(), 0, p, true);
            }
            try (var mesh = builder.buildOrThrow()) {
                return new Tile(RenderSystem.getDevice().createBuffer(() -> "Naturality aurora curtains", 32, mesh.vertexBuffer()), panels.size()*6);
            }
        }
    }
    private static void vertex(BufferBuilder builder, AuroraGeometry.Point point, float height,
            AuroraGeometry.Panel panel, boolean bottom) {
        // X-based texture coordinates match exactly where the two spline arms join.
        builder.addVertex(point.x(), point.y()+height, point.z()).setUv(point.x(), bottom ? panel.height() : 0)
            .setColor(panel.family()*85, Math.round(panel.height()), Math.round(panel.palette()*255), 255);
    }
    public static void render(RenderTarget target) {
        drawnPanels = 0;
        var client = Minecraft.getInstance();
        var world = AuroraRenderer.world;
        if (world == null || world != client.level || !world.dimension().equals(Level.OVERWORLD)
                || strength <= .001F) return;
        var camera = client.gameRenderer.mainCamera();
        var player = client.player;
        var colorView = target.getColorTextureView();
        if (colorView == null) return;
        if (camera.getFluidInCamera() != FogType.NONE || player == null
                || player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
                || player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS)) return;
        int configuredSegments = Math.clamp(NaturalityConfig.get().effects.auroraSegments, 1, 12);
        if (configuredSegments != tileSegments) {
            for (var tile : TILES.values()) tile.vertices.close();
            TILES.clear();
            tileSegments = configuredSegments;
        }
        var eye = camera.position();
        int tileX = (int)Math.floor(eye.x / AuroraGeometry.TILE), tileZ = (int)Math.floor(eye.z / AuroraGeometry.TILE);
        var iterator = TILES.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (Math.abs(entry.getKey().x-tileX)>1 || Math.abs(entry.getKey().z-tileZ)>1) {
                entry.getValue().vertices.close(); iterator.remove();
            }
        }
        for (int z = tileZ-1; z <= tileZ+1; z++) for (int x = tileX-1; x <= tileX+1; x++)
            TILES.computeIfAbsent(new Key(x,z), AuroraRenderer::build);
        // Texture reload/upload must finish before opening the draw pass.
        var palette = client.getTextureManager().getTexture(PALETTE);
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float value = previousStrength + (strength-previousStrength)*partial;
        float rain = 1 - world.getRainLevel(partial);
        float phase = motionPhase + partial * .05F * (.18F + .82F * value / 20F);
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Naturality aurora", colorView, Optional.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("Sampler0", palette.getTextureView(), palette.getSampler());
            for (var entry : TILES.entrySet()) {
                var tile = entry.getValue();
                var transform = RenderSystem.getModelViewMatrixCopy().translate(
                    (float)(entry.getKey().x*(double)AuroraGeometry.TILE-eye.x), (float)-eye.y,
                    (float)(entry.getKey().z*(double)AuroraGeometry.TILE-eye.z));
                pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(transform,
                    new Vector4f(value/20F, phase, rain, .9F)));
                pass.setVertexBuffer(0, tile.vertices.slice());
                pass.setIndexBuffer(indices.getBuffer(tile.indices), indices.type());
                pass.drawIndexed(tile.indices, 1, 0, 0, 0);
                drawnPanels += tile.indices/6;
            }
        }
    }
    public static void close() {
        for (var tile : TILES.values()) tile.vertices.close();
        TILES.clear(); world = null; previousStrength = strength = motionPhase = 0; drawnPanels = 0; tileSegments = -1;
    }
}
