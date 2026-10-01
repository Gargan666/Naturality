package naturality.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import java.util.*;
import java.util.Random;
import naturality.Naturality;
import naturality.sky.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.*;
import java.lang.Math;

/** Celestial sprites on a camera-centered sky sphere, never world particles. */
public final class MeteorShower implements AutoCloseable {
    private static final List<Meteor> METEORS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static final Identifier[] FRAMES = new Identifier[7];
    private static @org.jspecify.annotations.Nullable ClientLevel world;
    private static float spawnBudget;
    private @org.jspecify.annotations.Nullable GpuBuffer quad;
    static { for (int i = 0; i < 7; i++) FRAMES[i] = Naturality.id("textures/sky/meteor_shower_" + i + ".png"); }

    public static void initialize() {
        ClientPlayConnectionEvents.INIT.register((_, _) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(MeteorShower::tick);
    }
    private static void clear() { METEORS.clear(); world = null; spawnBudget = 0; }
    public static float strength() { return SkyEventsClient.strength(world, SkyEventType.METEOR_SHOWER); }
    public static int count() { return METEORS.size(); }
    private static void tick(Minecraft client) {
        if (world != client.level) { world = client.level; METEORS.clear(); spawnBudget = 0; }
        var world = MeteorShower.world;
        if (world == null || client.isPaused()) return;
        if (!isNight(world)) { METEORS.clear(); spawnBudget = 0; return; }
        METEORS.removeIf(m -> ++m.age >= m.duration + (7 - (m.start + 2)) * 2);
        if (!world.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;
        spawnBudget += strength() * .025F; // 0-10 new meteors/second over the entire hemisphere.
        while (spawnBudget >= 1 && METEORS.size() < 128) {
            spawnBudget--;
            METEORS.add(new Meteor(RANDOM.nextFloat() * (float)(Math.PI * 2),
                (float)Math.asin(.35 + RANDOM.nextDouble() * .64), RANDOM.nextInt(3),
                20 + RANDOM.nextInt(61), .004F + RANDOM.nextFloat() * .004F,
                3 + RANDOM.nextFloat() * 3));
        }
    }
    private static boolean isNight(ClientLevel level) {
        long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
        return time >= 13000 && time < 23000;
    }
    public static int frame(int start, int duration, float age) {
        return age < duration ? start + ((int)(age / 2) & 1) : start + 2 + (int)((age - duration) / 2);
    }
    public static float opacity(float age) { float t = Math.clamp(age / 6, 0, 1); return t * t * (3 - 2 * t); }
    private static final class Meteor {
        final float azimuth, elevation, speed, size;
        final int start, duration;
        int age;
        Meteor(float azimuth, float elevation, int start, int duration, float speed, float size) {
            this.azimuth = azimuth; this.elevation = elevation; this.start = start;
            this.duration = duration; this.speed = speed; this.size = size;
        }
    }
    public void prepare() {
        if (METEORS.isEmpty()) return;
        // Lazy texture registration uploads pixels, so resolve every frame before opening the sky pass.
        for (var frame : FRAMES) Minecraft.getInstance().getTextureManager().getTexture(frame);
        if (quad != null) return;
        try (var bytes = ByteBufferBuilder.exactlySized(4 * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
            var b = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);
            b.addVertex(-1, -1, 0).setUv(0, 1);
            b.addVertex(1, -1, 0).setUv(1, 1);
            b.addVertex(1, 1, 0).setUv(1, 0);
            b.addVertex(-1, 1, 0).setUv(0, 0);
            try (var mesh = b.buildOrThrow()) {
                quad = RenderSystem.getDevice().createBuffer(() -> "Naturality meteor quad", 32, mesh.vertexBuffer());
            }
        }
    }
    public void render(RenderPass pass, float rainBrightness) {
        var world = MeteorShower.world;
        var quad = this.quad;
        if (METEORS.isEmpty() || world == null || !world.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                || !isNight(world)) return;
        if (quad == null) return;
        var client = Minecraft.getInstance();
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.CELESTIAL));
        RenderSystem.bindDefaultUniforms(pass);
        pass.setVertexBuffer(0, quad.slice());
        pass.setIndexBuffer(indices.getBuffer(6), indices.type());
        for (var m : METEORS) {
            float age = m.age + partial;
            int frame = frame(m.start, m.duration, age);
            if (frame >= 7) continue;
            float s = (float)Math.sin(m.azimuth), c = (float)Math.cos(m.azimuth);
            float se = (float)Math.sin(m.elevation), ce = (float)Math.cos(m.elevation);
            var normal = new Vector3f(s * ce, se, c * ce);
            var right = new Vector3f(-c, 0, s);
            var up = new Vector3f(-s * se, ce, -c * se);
            // Follow a great circle with the head exactly aligned to its velocity, including near the zenith.
            var direction = new Vector3f(right).add(up).normalize().negate();
            float travel = age * m.speed, ct = (float)Math.cos(travel), st = (float)Math.sin(travel);
            var radial = new Vector3f(normal).mul(ct).fma(st, direction);
            var y = new Vector3f(normal).mul(st).fma(-ct, direction);
            var x = new Vector3f(radial).cross(y).mul(m.size * 3F / 14F);
            y.mul(m.size);
            var center = radial.mul(100);
            var model = new Matrix4f().setColumn(0, new Vector4f(x, 0)).setColumn(1, new Vector4f(y, 0))
                .setColumn(2, new Vector4f(0, 0, 1, 0)).setColumn(3, new Vector4f(center, 1));
            var transform = RenderSystem.getModelViewMatrixCopy().mul(model);
            var texture = client.getTextureManager().getTexture(FRAMES[frame]);
            pass.setUniform("Sampler0", texture.getTextureView(), texture.getSampler());
            pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(transform,
                new Vector4f(1, 1, 1, opacity(age) * rainBrightness)));
            pass.drawIndexed(6, 1, 0, 0, 0);
        }
    }
    @Override public void close() { if (quad != null) { quad.close(); quad = null; } }
}



