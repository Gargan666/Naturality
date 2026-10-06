package naturality.client.villager;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.resources.Identifier;

/** Shared player/villager line, anchored at the top of the authored bobber stem. */
public final class FishingLineRendering {
    private static final RenderType LINE = RenderType.create("naturality_fishing_line",
        RenderSetup.builder(RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("naturality", "pipeline/fishing_line"))
            .withVertexShader(Identifier.fromNamespaceAndPath("naturality", "core/fishing_line"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("naturality", "core/fishing_line"))
            .withColorTargetState(ColorTargetState.DEFAULT).build())).createRenderSetup());
    private static final RenderType LIT_LINE = RenderType.create("naturality_lead",
        RenderSetup.builder(RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("naturality", "pipeline/lead"))
            .withVertexShader(Identifier.fromNamespaceAndPath("naturality", "core/fishing_line"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("naturality", "core/lead"))
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER2)
            .withColorTargetState(ColorTargetState.DEFAULT).build())).useLightmap().createRenderSetup());
    public static final float ANCHOR_Y = .07F + 4.0F / 16.0F * 1.3F;
    public static final float VANILLA_ANCHOR_Y = .25F;

    private FishingLineRendering() { }

    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Object key,
            Vec3 origin, java.util.function.Supplier<Vec3> endpoint) {
        var client = Minecraft.getInstance();
        var frame = client.gameRenderer.gameRenderState();
        float pixelSize = frame.optionsRenderState.cameraType.isFirstPerson()
            && FirstPersonRodTip.worldTip != null ? FirstPersonRodTip.pixelSize : 1.0F / 32;
        submitRope(poseStack, collector, key, origin, () -> origin.add(endpoint.get()),
            new Vec3(0, ANCHOR_Y, 0), pixelSize, _ -> -16777216);
    }

    /** Shared camera-facing geometry and chain simulation for fishing and leads. */
    public static void submitRope(PoseStack poseStack, SubmitNodeCollector collector, Object key,
            Vec3 origin, java.util.function.Supplier<Vec3> end, Vec3 localOrigin,
            float thickness, java.util.function.IntUnaryOperator color) {
        submitRope(poseStack, collector, key, origin, end, localOrigin, thickness, color, LINE);
    }

    /** Lead colors carry packed block/sky light in alpha; the shader restores opacity. */
    public static void submitLitRope(PoseStack poseStack, SubmitNodeCollector collector, Object key,
            Vec3 origin, java.util.function.Supplier<Vec3> end, Vec3 localOrigin,
            float thickness, java.util.function.IntUnaryOperator color) {
        submitRope(poseStack, collector, key, origin, end, localOrigin, thickness, color, LIT_LINE);
    }

    private static void submitRope(PoseStack poseStack, SubmitNodeCollector collector, Object key,
            Vec3 origin, java.util.function.Supplier<Vec3> end, Vec3 localOrigin,
            float thickness, java.util.function.IntUnaryOperator color, RenderType renderType) {
        var client = Minecraft.getInstance();
        float projectionPixels = client.getWindow().getHeight() * Math.abs(client.gameRenderer
            .gameRenderState().levelRenderState.cameraRenderState.projectionMatrix.m11()) * .5F;
        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            RopeSimulation chain = RopeChain.sampleChain(key, origin, end.get()).simulation;
            Vertex first = VERTICES.get()[0], second = VERTICES.get()[1];
            first.prepare(pose, chain, 0, origin, localOrigin, thickness * projectionPixels);
            // Both ends of a band retain the same color/lightmap value.
            for (int i = 0; i < RopeSimulation.SEGMENTS; i++) {
                second.prepare(pose, chain, i + 1, origin, localOrigin, thickness * projectionPixels);
                int bandColor = color.applyAsInt(i);
                first.emit(buffer, pose, bandColor);
                second.emit(buffer, pose, bandColor);
                Vertex swap = first; first = second; second = swap;
            }
        });
    }

    private static final ThreadLocal<Vertex[]> VERTICES = ThreadLocal.withInitial(
        () -> new Vertex[] {new Vertex(), new Vertex()});

    private static final class Vertex {
        private final org.joml.Vector3f transformed = new org.joml.Vector3f();
        private float x, y, z, nx, ny, nz, width;

        void prepare(PoseStack.Pose pose, RopeSimulation chain, int i,
                Vec3 origin, Vec3 localOrigin, float projectedPixelSize) {
            x = (float)(chain.x[i] - origin.x + localOrigin.x);
            y = (float)(chain.y[i] - origin.y + localOrigin.y);
            z = (float)(chain.z[i] - origin.z + localOrigin.z);
            int next = Math.min(RopeSimulation.SEGMENTS, i + 1), previous = Math.max(0, i - 1);
            double dx = chain.x[next] - chain.x[previous], dy = chain.y[next] - chain.y[previous];
            double dz = chain.z[next] - chain.z[previous];
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.0e-5) { nx = 0; ny = 1; nz = 0; }
            else { nx = (float)(dx / length); ny = (float)(dy / length); nz = (float)(dz / length); }
            float distance = transformed.set(x, y, z).mulPosition(pose.pose()).length();
            width = Mth.clamp(projectedPixelSize / Math.max(.25F, distance), .25F, 32.0F);
        }

        void emit(VertexConsumer buffer, PoseStack.Pose pose, int color) {
            buffer.addVertex(pose, x, y, z).setColor(color)
                .setNormal(pose, nx, ny, nz).setLineWidth(width);
        }
    }
}
