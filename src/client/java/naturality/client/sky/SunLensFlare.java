package naturality.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.*;
import java.util.Optional;
import naturality.Naturality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import naturality.client.mixin.SunSpriteAccess;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Captures the sun during sky rendering, then draws camera ghosts just before GUI. */
public final class SunLensFlare implements AutoCloseable {
    private static final int ELEMENTS = 6;
    private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder()
        .withLocation(Naturality.id("pipeline/sun_lens_flare"))
        .withVertexShader(Naturality.id("core/sun_lens_flare"))
        .withFragmentShader(Naturality.id("core/sun_lens_flare"))
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withCull(false)
        .withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(BlendFunction.OVERLAY))
        .build());
    private @org.jspecify.annotations.Nullable GpuBuffer vertices;
    private static @org.jspecify.annotations.Nullable SunLensFlare pending;
    private @org.jspecify.annotations.Nullable GpuBufferSlice projection;
    private final Matrix4f parameters = new Matrix4f();
    private final Vector4f tint = new Vector4f();
    private final java.util.List<Vector3f> sourceSamples = new java.util.ArrayList<>();
    private long visibilityTick = Long.MIN_VALUE;
    private java.lang.ref.WeakReference<net.minecraft.client.multiplayer.ClientLevel> visibilityLevel = new java.lang.ref.WeakReference<>(null);
    private float visibleFraction;
    private final java.util.List<Vec3> clearSunRays = new java.util.ArrayList<>();
    private Vec3 visibilityEye = Vec3.ZERO;

    /** Sample the opaque sun face, excluding the texture's transparent halo. */
    public void updateSource(TextureAtlasSprite sprite) {
        var image = ((SunSpriteAccess) sprite.contents()).naturality$sunImage();
        int width = image.getWidth(), height = image.getHeight();
        int minX = width, minY = height, maxX = -1, maxY = -1;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (ARGB.alpha(image.getPixel(x, y)) < 250) continue;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        sourceSamples.clear();
        if (maxX >= minX && maxY >= minY) {
            for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) {
                float u = minX + (x + 0.5F) * (maxX - minX + 1) / 5;
                float v = minY + (y + 0.5F) * (maxY - minY + 1) / 5;
                if (ARGB.alpha(image.getPixel((int) u, (int) v)) < 250) continue;
                sourceSamples.add(new Vector3f(60 * u / width - 30, 100, 60 * v / height - 30));
            }
        }
        visibilityTick = Long.MIN_VALUE;
    }

    public static float visibilityStrength(float fraction) {
        float t = Math.clamp((fraction - 0.30F) / 0.40F, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private float sourceVisibility(PoseStack sunPose) {
        var client = Minecraft.getInstance();
        var level = client.level;
        if (level == null || client.player == null || sourceSamples.isEmpty()) return 0;
        // At most 25 visual-shape rays per tick, independent of frame rate.
        if (visibilityLevel.get() != level || visibilityTick != level.getGameTime()) {
            visibilityLevel = new java.lang.ref.WeakReference<>(level);
            visibilityTick = level.getGameTime();
            var eye = client.gameRenderer.mainCamera().position();
            visibilityEye = eye;
            clearSunRays.clear();
            double range = client.options.getEffectiveRenderDistance() * 16.0;
            var ends = new java.util.ArrayList<Vec3>(sourceSamples.size());
            var bounds = new net.minecraft.world.phys.AABB(eye, eye);
            for (var sample : sourceSamples) {
                var ray = sunPose.last().pose().transformDirection(new Vector3f(sample)).normalize();
                var end = eye.add(new Vec3(ray.x, ray.y, ray.z).scale(range));
                ends.add(end);
                bounds = bounds.minmax(new net.minecraft.world.phys.AABB(eye, end));
            }
            // Query the sun-ray volume once, rather than searching entities per ray.
            var cameraEntity = client.getCameraEntity();
            var blockers = level.getEntities((net.minecraft.world.entity.Entity) null, bounds,
                entity -> !entity.isRemoved() && !entity.isInvisible() && !entity.isSpectator()
                    && (entity != cameraEntity || !client.options.getCameraType().isFirstPerson())
                    && entity.shouldRenderAtSqrDistance(entity.distanceToSqr(eye)));
            int visible = 0;
            for (var end : ends) {
                if (level.clip(new ClipContext(eye, end, ClipContext.Block.VISUAL,
                        ClipContext.Fluid.NONE, client.player)).getType() != HitResult.Type.MISS) continue;
                boolean blocked = false;
                for (var entity : blockers) {
                    var box = entity.getBoundingBox();
                    if (box.contains(eye) || box.clip(eye, end).isPresent()) {
                        blocked = true;
                        break;
                    }
                }
                if (!blocked) { visible++; clearSunRays.add(end); }
            }
            visibleFraction = (float) visible / sourceSamples.size();
        }
        return visibilityStrength(visibleFraction);
    }

    public static void beginFrame() {
        pending = null;
        naturality.client.cloud.CloudSunOcclusion.beginFrame();
    }

    public static void renderOverlay() {
        var effect = pending;
        pending = null;
        if (effect == null) return;
        float transmitted = 0;
        for (var end : effect.clearSunRays)
            transmitted += naturality.client.cloud.CloudSunOcclusion.transmission(effect.visibilityEye, end);
        float visibility = effect.sourceSamples.isEmpty() ? 0
            : visibilityStrength(transmitted / effect.sourceSamples.size());
        if (visibility <= 0) return;
        effect.tint.w *= visibility;
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView();
        if (target == null) return;
        // Lazy loading can upload textures, which must happen outside a render pass.
        var textures = new net.minecraft.client.renderer.texture.AbstractTexture[ELEMENTS];
        var manager = Minecraft.getInstance().getTextureManager();
        for (int i = 0; i < ELEMENTS; i++)
            textures[i] = manager.getTexture(Naturality.id("textures/misc/sun_flare_" + i + ".png"));
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Naturality camera lens flare", target, Optional.empty())) {
            effect.draw(pass, textures);
        }
    }

    /** Brightens toward the sun, reaching maximum strength at exact center. */
    public static float strength(float closeness) {
        return 0.14F * (0.25F + 0.75F * closeness);
    }

    private void build() {
        try (var bytes = ByteBufferBuilder.exactlySized(ELEMENTS * 4 * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
            var builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);
            for (int i = 0; i < ELEMENTS; i++) {
                // Z is the element index; XY and UV describe the sprite's own quad.
                builder.addVertex(-1, -1, i).setUv(0, 0);
                builder.addVertex(1, -1, i).setUv(1, 0);
                builder.addVertex(1, 1, i).setUv(1, 1);
                builder.addVertex(-1, 1, i).setUv(0, 1);
            }
            try (var mesh = builder.buildOrThrow()) {
                vertices = RenderSystem.getDevice().createBuffer(() -> "Naturality sun lens flare", 32, mesh.vertexBuffer());
            }
        }
    }

    public void prepare(Matrix4f sunView, PoseStack sunPose, float closeness, float rain, int color) {
        float alpha = strength(closeness) * rain
            * Math.clamp(naturality.config.NaturalityConfig.get().effects.cameraFlareMaxOpacity, 0, 100) / 14F;
        if (alpha <= 0.00001F || sunView.m32() >= 0) return;
        if (sourceVisibility(sunPose) <= 0) return;
        var direction = sunPose.last().pose().transformDirection(new Vector3f(0, 1, 0)).normalize();
        // The caller already includes the celestial horizon fade in brightness.
        // Keep the flare alive through sunrise/sunset while the sun is visible.
        float warmth = Math.clamp((0.45F - direction.y) / 0.45F, 0, 1);
        warmth = warmth * warmth * (3 - 2 * warmth);
        projection = RenderSystem.getProjectionMatrixBuffer();
        parameters.translation(sunView.m30(), sunView.m31(), sunView.m32()).scale(closeness, warmth, 1);
        tint.set(ARGB.red(color) / 255F, ARGB.green(color) / 255F, ARGB.blue(color) / 255F, alpha);
        pending = this;
    }

    private void draw(RenderPass pass, net.minecraft.client.renderer.texture.AbstractTexture[] textures) {
        var capturedProjection = projection;
        if (capturedProjection == null) return;
        if (vertices == null) build();
        var buffer = vertices;
        if (buffer == null) return;
        // The shader projects only the translation (the actual rendered sun).
        // X carries shared closeness; Y carries the sun-altitude palette blend.
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("Projection", capturedProjection);
        pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(parameters, tint));
        pass.setVertexBuffer(0, buffer.slice());
        pass.setIndexBuffer(indices.getBuffer(ELEMENTS * 6), indices.type());
        for (int i = 0; i < ELEMENTS; i++) {
            var texture = textures[i];
            pass.setUniform("Sampler0", texture.getTextureView(), texture.getSampler());
            pass.drawIndexed(6, 1, 0, i * 4, 0);
        }
    }

    @Override public void close() {
        if (pending == this) pending = null;
        projection = null;
        visibilityLevel.clear();
        if (vertices != null) { vertices.close(); vertices = null; }
    }
}
