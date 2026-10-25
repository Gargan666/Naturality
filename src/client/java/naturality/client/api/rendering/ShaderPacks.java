package naturality.client.api.rendering;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.WeakHashMap;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.backend.opengl.GlRenderPipeline;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.oit.OitStage;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL33C;

/** Rendering ownership at the optional shader-pack boundary; gameplay and geometry stay independent. */
@org.jspecify.annotations.NullMarked
public final class ShaderPacks {
    private static final @org.jspecify.annotations.Nullable MethodHandle ACTIVE = findActiveQuery();
    private static volatile boolean terrainActive;
    private static final Set<RenderPipeline> FLAT_PARTICLES = Collections.synchronizedSet(
        Collections.newSetFromMap(new IdentityHashMap<>()));
    private static final Set<RenderPipeline> ASSIGNED_PARTICLES = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<RenderPipeline> SCENE_PIPELINES = Collections.synchronizedSet(
        Collections.newSetFromMap(new IdentityHashMap<>()));
    // Pipeline reloads release old compiled backends without retaining their GPU programs here.
    private static final Set<GlRenderPipeline> SCENE_BACKENDS = Collections.synchronizedSet(
        Collections.newSetFromMap(new WeakHashMap<>()));
    private static @org.jspecify.annotations.Nullable GpuTexture identityTexture;
    private static @org.jspecify.annotations.Nullable GpuTextureView identityView;

    private ShaderPacks() {}

    private static @org.jspecify.annotations.Nullable MethodHandle findActiveQuery() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) return null;
        try {
            var api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            var instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup().findVirtual(api, "isShaderPackInUse",
                MethodType.methodType(boolean.class)).bindTo(instance);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot query the installed Iris shader-pack API", failure);
        }
    }

    /**
     * Query current rendering ownership, including live shader toggles. Iris stores
     * scene color in its own targets while a pack is active, so native scene-copy,
     * atmosphere and water-composition passes must defer to that pack. Merely
     * installing Iris does not change the native path. No optional type is linked.
     */
    public static boolean active() {
        var query = ACTIVE;
        if (query == null) return false;
        try {
            return (boolean) query.invokeExact();
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            // MethodHandle's signature declares Throwable; the public API does not.
            throw new IllegalStateException("Cannot query Iris shader-pack rendering ownership", failure);
        }
    }

    /**
     * Shader ownership published before chunk rebuilds. Mesh workers must use this
     * snapshot rather than reading Iris's render-thread-owned pipeline reference.
     */
    public static boolean terrainActive() {
        return terrainActive;
    }

    /**
     * Publish the selected shader contract on the client thread. A change tells
     * the caller to rebuild terrain if Iris did not already request that rebuild.
     */
    public static boolean updateTerrainOwnership() {
        if (!net.minecraft.client.Minecraft.getInstance().isSameThread())
            throw new IllegalStateException("Terrain shaders must be selected on the client thread");
        boolean active = active();
        boolean changed = terrainActive != active;
        terrainActive = active;
        return changed;
    }

    /** Only feature-owned, untextured PARTICLE geometry may opt into this adapter. */
    public static void registerFlatParticles(RenderPipeline pipeline, OitPipelineSet oit) {
        if (pipeline.getVertexFormatBinding(0) != DefaultVertexFormat.PARTICLE)
            throw new IllegalArgumentException("Flat-particle adapter requires the native particle vertex format");
        FLAT_PARTICLES.add(pipeline);
        for (var stage : OitStage.values()) FLAT_PARTICLES.add(oit.getPipeline(stage));
    }

    public static boolean flatParticles(RenderPipeline pipeline) {
        return active() && FLAT_PARTICLES.contains(pipeline);
    }

    /** Classify only opted-in particle draws; Iris retains the pipeline's depth/blend state. */
    public static RenderPipeline particlePipeline(RenderPipeline pipeline) {
        if (flatParticles(pipeline) && ASSIGNED_PARTICLES.add(pipeline)) {
            try {
                ParticleApi.ASSIGN.invokeExact(pipeline);
                ParticleApi.ASSIGN_SHADOW.invokeExact(pipeline);
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                throw new IllegalStateException("Cannot classify flat particles for Iris", failure);
            }
        }
        return pipeline;
    }

    private static final class ParticleApi {
        private static final MethodHandle ASSIGN = findAssignment("IrisProgram", "PARTICLES_TRANSLUCENT", "assignPipeline");
        private static final MethodHandle ASSIGN_SHADOW = findAssignment("IrisShadowProgram", "SHADOW", "assignPipelineShadow");
        private static MethodHandle findAssignment(String enumName, String programName, String methodName) {
            try {
                var api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                var instance = api.getMethod("getInstance").invoke(null);
                var programs = Class.forName("net.irisshaders.iris.api.v0." + enumName);
                var program = programs.getField(programName).get(null);
                var assign = MethodHandles.publicLookup().findVirtual(api, methodName,
                    MethodType.methodType(void.class, RenderPipeline.class, programs)).bindTo(instance);
                return MethodHandles.insertArguments(assign, 1, program);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot access the Iris particle pipeline API", failure);
            }
        }
    }

    /** Opt in one native, encoded world pipeline whose scene target was verified to need routing. */
    public static void registerScenePipeline(RenderPipeline pipeline) {
        SCENE_PIPELINES.add(pipeline);
    }

    /** Associate the actual backend without changing its shader, attributes, blending or depth state. */
    public static void compiledScenePipeline(RenderPipeline pipeline, CompiledRenderPipeline compiled) {
        if (SCENE_PIPELINES.contains(pipeline) && compiled instanceof FrontendRenderPipeline frontend
                && frontend.backendRenderPipeline() instanceof GlRenderPipeline backend)
            SCENE_BACKENDS.add(backend);
    }

    /**
     * Bind only an opted-in classic world draw after the encoder has set up its
     * program. Iris 1.11.7 otherwise selects the native color target for this
     * custom program. Borrow its current fallback target, retaining the existing
     * GLSL and encoded vertices. The caller must restore the returned bindings in
     * finally. No GPU queries, saved-state objects or target ownership are needed.
     */
    public static long beginSceneDraw(@Nullable GlRenderPipeline pipeline, @Nullable RenderPassDescriptor descriptor) {
        if (pipeline == null || descriptor == null || !SCENE_BACKENDS.contains(pipeline)) return -1;
        var client = Minecraft.getInstance();
        if (client.gameRenderer.useImprovedTransparency()) return -1;
        var main = client.gameRenderer.mainRenderTarget();
        var colors = descriptor.colorAttachments();
        var depth = descriptor.depthAttachment();
        if (colors.size() != 1 || depth == null) return -1;
        var color = colors.getFirst();
        if (color == null || color.textureView().texture() != main.getColorTexture()
                || depth.textureView().texture() != main.getDepthTexture()
                || color.textureView().baseMipLevel() != 0 || depth.textureView().baseMipLevel() != 0) return -1;
        var area = descriptor.renderArea();
        if (area.x() != 0 || area.y() != 0 || area.width() != main.width || area.height() != main.height) return -1;
        var api = SceneApi.INSTANCE;
        if (api == null) return -1;
        int target = api.target(main.width, main.height, depth.textureView().texture());
        if (target == 0) return -1;
        int read = GlStateManager.getFrameBuffer(GL33C.GL_READ_FRAMEBUFFER);
        int draw = GlStateManager.getFrameBuffer(GL33C.GL_DRAW_FRAMEBUFFER);
        if (read == target && draw == target) return -1;
        GlStateManager._glBindFramebuffer(GL33C.GL_FRAMEBUFFER, target);
        return (long) read << 32 | Integer.toUnsignedLong(draw);
    }

    public static void endSceneDraw(long bindings) {
        if (bindings == -1) return;
        GlStateManager._glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, (int) (bindings >>> 32));
        GlStateManager._glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, (int) bindings);
    }

    /**
     * Iris has no public API for retaining a custom program while choosing its
     * scene target. This lazy adapter is limited to the inspected 1.11.7/26.3
     * contract; it never retains a pipeline, framebuffer or texture across draws.
     */
    private static final class SceneApi {
        private static final @Nullable SceneApi INSTANCE = find();
        private final Class<?> pipelineType;
        private final MethodHandle current, world, shadow, phase, before;
        private final MethodHandle targets, directives, fallbackIndex, targetAt, width, height, depth;
        private final MethodHandle defaultBefore, defaultAfter, framebufferId;
        private final Object handSolid, handTranslucent;

        private SceneApi() throws ReflectiveOperationException {
            var lookup = MethodHandles.publicLookup();
            var iris = Class.forName("net.irisshaders.iris.Iris");
            var manager = Class.forName("net.irisshaders.iris.pipeline.PipelineManager");
            var worldPipeline = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPipeline");
            pipelineType = Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline");
            var phases = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPhase");
            var renderTargets = Class.forName("net.irisshaders.iris.targets.RenderTargets");
            var renderTarget = Class.forName("net.irisshaders.iris.targets.RenderTarget");
            var packDirectives = Class.forName("net.irisshaders.iris.shaderpack.properties.PackDirectives");
            var framebuffer = Class.forName("net.irisshaders.iris.gl.framebuffer.GlFramebuffer");
            current = MethodHandles.filterReturnValue(
                lookup.findStatic(iris, "getPipelineManager", MethodType.methodType(manager)),
                lookup.findVirtual(manager, "getPipelineNullable", MethodType.methodType(worldPipeline)))
                .asType(MethodType.methodType(Object.class));
            world = method(pipelineType, "shouldOverrideShaders", boolean.class);
            shadow = lookup.findStatic(Class.forName("net.irisshaders.iris.shadows.ShadowRenderingState"),
                "areShadowsCurrentlyBeingRendered", MethodType.methodType(boolean.class));
            phase = method(pipelineType, "getPhase", phases).asType(MethodType.methodType(Object.class, Object.class));
            handSolid = java.util.Objects.requireNonNull(phases.getField("HAND_SOLID").get(null));
            handTranslucent = java.util.Objects.requireNonNull(phases.getField("HAND_TRANSLUCENT").get(null));
            before = getter(pipelineType, "isBeforeTranslucent", boolean.class);
            targets = getter(pipelineType, "renderTargets", renderTargets);
            directives = getter(pipelineType, "packDirectives", packDirectives);
            fallbackIndex = method(packDirectives, "getFallbackTex", int.class);
            targetAt = lookup.findVirtual(renderTargets, "get", MethodType.methodType(renderTarget, int.class))
                .asType(MethodType.methodType(Object.class, Object.class, int.class));
            width = method(renderTarget, "getWidth", int.class);
            height = method(renderTarget, "getHeight", int.class);
            depth = method(renderTargets, "getDepthTexture", GpuTexture.class);
            defaultBefore = getter(pipelineType, "defaultFB", framebuffer);
            defaultAfter = getter(pipelineType, "defaultFBAlt", framebuffer);
            framebufferId = method(framebuffer, "getId", int.class);
        }

        private static MethodHandle method(Class<?> owner, String name, Class<?> result) throws ReflectiveOperationException {
            return MethodHandles.publicLookup().findVirtual(owner, name, MethodType.methodType(result))
                .asType(MethodType.methodType(result, Object.class));
        }

        private static MethodHandle getter(Class<?> owner, String name, Class<?> result) throws ReflectiveOperationException {
            return MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).findGetter(owner, name, result)
                .asType(MethodType.methodType(result.isPrimitive() ? result : Object.class, Object.class));
        }

        private static @Nullable SceneApi find() {
            var iris = FabricLoader.getInstance().getModContainer("iris");
            if (iris.isEmpty()) return null;
            String version = iris.get().getMetadata().getVersion().getFriendlyString();
            if (!version.equals("1.11.7+mc26.3")) {
                naturality.Naturality.LOGGER.warn(
                    "Custom scene-target integration requires the inspected Iris 1.11.7+mc26.3 contract; found {}", version);
                return null;
            }
            try {
                return new SceneApi();
            } catch (ReflectiveOperationException failure) {
                naturality.Naturality.LOGGER.warn(
                    "Cannot access Iris scene-target integration; retaining the original custom draw", failure);
                return null;
            }
        }

        private int target(int sceneWidth, int sceneHeight, GpuTexture sceneDepth) {
            try {
                Object pipeline = current.invokeExact();
                // shouldOverrideShaders requires both world rendering and main-target
                // ownership. Together with the descriptor guard this excludes GUI,
                // offscreen previews, native mode and shader-pack shadow passes.
                if (!pipelineType.isInstance(pipeline) || !(boolean) world.invokeExact(pipeline)
                        || (boolean) shadow.invokeExact()) return 0;
                Object renderPhase = phase.invokeExact(pipeline);
                if (renderPhase == handSolid || renderPhase == handTranslucent) return 0;
                Object buffers = targets.invokeExact(pipeline);
                if ((GpuTexture) depth.invokeExact(buffers) != sceneDepth) return 0;
                Object settings = directives.invokeExact(pipeline);
                int index = (int) fallbackIndex.invokeExact(settings);
                Object color = targetAt.invokeExact(buffers, index);
                if (color == null || (int) width.invokeExact(color) != sceneWidth
                        || (int) height.invokeExact(color) != sceneHeight) return 0;
                Object framebuffer = (boolean) before.invokeExact(pipeline)
                    ? defaultBefore.invokeExact(pipeline) : defaultAfter.invokeExact(pipeline);
                return (int) framebufferId.invokeExact(framebuffer);
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                throw new IllegalStateException("Cannot select the current Iris scene target", failure);
            }
        }
    }

    /** Allocate before opening a render pass; live pack disable releases the tiny GPU resource. */
    public static void prepareFrame() {
        if (!active()) { close(); return; }
        if (identityTexture != null) return;
        // Existing rims carry their complete color in each vertex and ignore UV0.
        // Iris's particle shader multiplies by Sampler0. This GPU-only identity
        // preserves that color; it is not a replacement sprite or an art asset.
        var texture = RenderSystem.getDevice().createTexture(() -> "Naturality particle sampler identity",
            GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
            GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
        identityTexture = texture;
        identityView = RenderSystem.getDevice().createTextureView(texture);
        // The device returns its shared frame encoder: this clear precedes every
        // particle draw and is submitted with the frame, without an extra fence.
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture, new Vector4f(1));
    }

    public static GpuTextureView particleSampler() {
        return java.util.Objects.requireNonNull(identityView, "Particle sampler must be prepared before rendering");
    }

    /** Renderer reload/shutdown releases views before textures; constant data needs no asset reload. */
    public static void close() {
        if (identityView != null) { identityView.close(); identityView = null; }
        if (identityTexture != null) { identityTexture.close(); identityTexture = null; }
    }
}
