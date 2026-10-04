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
            new Vec3(0, ANCHOR_Y, 0), pixelSize, ignored -> -16777216);
    }

    /** Shared camera-facing geometry and chain simulation for fishing and leads. */
    public static void submitRope(PoseStack poseStack, SubmitNodeCollector collector, Object key,
            Vec3 origin, java.util.function.Supplier<Vec3> end, Vec3 localOrigin,
            float thickness, java.util.function.IntUnaryOperator color) {
        var client = Minecraft.getInstance();
        float projectionPixels = client.getWindow().getHeight() * Math.abs(client.gameRenderer
            .gameRenderState().levelRenderState.cameraRenderState.projectionMatrix.m11()) * .5F;
        collector.submitCustomGeometry(poseStack, LINE, (pose, buffer) -> {
            Vec3[] chain = RopeChain.sample(key, origin, end.get());
            for (int i = 0; i < chain.length - 1; i++) {
                vertex(buffer, pose, chain, i, origin, localOrigin, thickness * projectionPixels, color.applyAsInt(i));
                vertex(buffer, pose, chain, i + 1, origin, localOrigin, thickness * projectionPixels, color.applyAsInt(i + 1));
            }
        });
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3[] chain,
            int index, Vec3 origin, Vec3 localOrigin, float projectedPixelSize, int color) {
        Vec3 point = chain[index].subtract(origin).add(localOrigin);
        Vec3 tangent = chain[Math.min(chain.length - 1, index + 1)]
            .subtract(chain[Math.max(0, index - 1)]).normalize();
        if (tangent.lengthSqr() < 1.0e-8) tangent = new Vec3(0, 1, 0);
        float distance = new org.joml.Vector3f((float)point.x, (float)point.y, (float)point.z)
            .mulPosition(pose.pose()).length();
        float width = Mth.clamp(projectedPixelSize / Math.max(.25F, distance), .25F, 32.0F);
        buffer.addVertex(pose, (float)point.x, (float)point.y, (float)point.z).setColor(color)
            .setNormal(pose, (float)tangent.x, (float)tangent.y, (float)tangent.z).setLineWidth(width);
    }
}
