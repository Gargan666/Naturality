package naturality.client.portal;

import java.util.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;
import naturality.portal.PortalCrossing;
import naturality.client.particle.*;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.state.level.*;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.*;

/** Shared geometry for entity silhouettes and frame edges clipped by those silhouettes. */
public final class PortalEntityGlowState extends QuadParticleRenderState {
    private static final RenderPipeline.Snippet EFFECT = RenderPipeline.builder()
        .withVertexShader(Naturality.id("core/portal_opening"))
        .withFragmentShader(Naturality.id("core/portal_entity_glow")).withCull(false).buildSnippet();
    private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET, EFFECT)
        .withLocation(Naturality.id("pipeline/portal_entity_glow"))
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
    private static final OitPipelineSet OIT = RenderPipelines.register(OitPipelineSet.builder("naturality_entity_glow",
        RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET, EFFECT)).build());
    private static final SingleQuadParticle.Layer SURFACE = new SingleQuadParticle.Layer(true, PortalGlowAppearance.PALETTE_TEXTURE, PIPELINE, OIT);
    private record Vertex(Vec3 position, float u, float v) { }
    private record Quad(SingleQuadParticle.Layer layer, Vertex a, Vertex b, Vertex c, Vertex d, int color) { }
    private final List<Quad> quads = new ArrayList<>();
    public static void initialize() { }

    public void add(PortalCrossing crossing, PortalGlowOcclusion.Rect rect, Vec3 eye, float strength,
                    List<PortalGlowOcclusion.Rect> blockers) {
        boolean xAxis = crossing.axis == Direction.Axis.X;
        double left = xAxis ? crossing.min.getX() : crossing.min.getZ();
        double right = (xAxis ? crossing.max.getX() : crossing.max.getZ()) + 1;
        double bottom = crossing.min.getY(), top = crossing.max.getY() + 1;
        double center = crossing.plane - crossing.side * 0.125;
        float pulse = PortalOpeningClient.pulse(crossing.anchor, 1);
        int rayColor = ARGB.colorFromFloat(strength * PortalGlowController.opacity(), 1 - pulse, 1, 1);
        // Each exposed silhouette edge faces the remaining open part of the portal.
        if (rect.left() > left) edge(crossing.axis, center, crossing.side, rect.left(), Math.max(bottom, rect.bottom()), Math.min(top, rect.top()),
            false, -1, blockers, eye, rayColor, pulse);
        if (rect.right() < right) edge(crossing.axis, center, crossing.side, rect.right(), Math.max(bottom, rect.bottom()), Math.min(top, rect.top()),
            false, 1, blockers, eye, rayColor, pulse);
        if (rect.bottom() > bottom) edge(crossing.axis, center, crossing.side, rect.bottom(), Math.max(left, rect.left()), Math.min(right, rect.right()),
            true, -1, blockers, eye, rayColor, pulse);
        if (rect.top() < top) edge(crossing.axis, center, crossing.side, rect.top(), Math.max(left, rect.left()), Math.min(right, rect.right()),
            true, 1, blockers, eye, rayColor, pulse);

        // A square distance field fills the surface halo, including all four corners.
        // Shift the gradient inward by one world texture pixel, including the inner
        // silhouette boundary without drawing rims along joins between model parts.
        int reach = 1 + (int)Math.floor(strength * 3);
        int u0 = (int)Math.floor(Math.max(left, rect.left() - reach / 16.0) * 16);
        int u1 = (int)Math.ceil(Math.min(right, rect.right() + reach / 16.0) * 16);
        int y0 = (int)Math.floor(Math.max(bottom, rect.bottom() - reach / 16.0) * 16);
        int y1 = (int)Math.ceil(Math.min(top, rect.top() + reach / 16.0) * 16);
        int color = ARGB.colorFromFloat(strength * 0.9F, 1, 1, 1);
        for (int u = u0; u < u1; u++) for (int y = y0; y < y1; y++) {
            double midU = (u + 0.5) / 16, midY = (y + 0.5) / 16;
            double distance = PortalGlowOcclusion.distance(rect, midU, midY);
            double shiftedDistance = distance + 1.0 / 16;
            if (shiftedDistance <= 0 || shiftedDistance >= reach / 16.0) continue;
            if (distance <= 0) {
                boolean boundary = false;
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    double sampleU = midU + dx / 16.0, sampleY = midY + dy / 16.0;
                    boolean occupied = rect.contains(sampleU, sampleY);
                    for (var other : blockers) if (other.contains(sampleU, sampleY)) { occupied = true; break; }
                    if (!occupied) boundary = true;
                }
                if (!boundary) continue;
            }
            boolean blocked = false;
            for (var other : blockers) {
                double otherDistance = PortalGlowOcclusion.distance(other, midU, midY);
                // One owner per halo pixel prevents overlapping model parts from doubling opacity.
                boolean earlier = other.left() < rect.left() || other.left() == rect.left()
                    && (other.bottom() < rect.bottom() || other.bottom() == rect.bottom()
                    && (other.right() < rect.right() || other.right() == rect.right() && other.top() < rect.top()));
                if (otherDistance < distance || otherDistance == distance && earlier) {
                    blocked = true; break;
                }
            }
            if (blocked) continue;
            float row = (float)(Math.floor(shiftedDistance * 16) / 16);
            double plane = crossing.plane + crossing.side * PortalGlowOcclusion.HALO_OFFSET;
            quads.add(new Quad(SURFACE,
                vertex(crossing.axis, u / 16.0, y / 16.0, plane, eye, row, reach / 16F),
                vertex(crossing.axis, (u + 1) / 16.0, y / 16.0, plane, eye, row, reach / 16F),
                vertex(crossing.axis, (u + 1) / 16.0, (y + 1) / 16.0, plane, eye, row, reach / 16F),
                vertex(crossing.axis, u / 16.0, (y + 1) / 16.0, plane, eye, row, reach / 16F), color));
        }
    }

    private void edge(Direction.Axis axis, double center, int side, double fixed, double start, double end,
                      boolean horizontal, int normal, List<PortalGlowOcclusion.Rect> blockers, Vec3 eye, int color, float pulse) {
        List<PortalGlowOcclusion.Interval> cuts = new ArrayList<>();
        double sample = fixed + normal / 1024.0;
        for (var other : blockers) {
            if (horizontal ? sample > other.bottom() && sample < other.top() : sample > other.left() && sample < other.right())
                cuts.add(horizontal ? new PortalGlowOcclusion.Interval(other.left(), other.right())
                    : new PortalGlowOcclusion.Interval(other.bottom(), other.top()));
        }
        for (var interval : PortalGlowOcclusion.uncovered(start, end, cuts))
            ray(axis, center, side, fixed + normal * PortalGlowGeometry.INSET, interval.start(), interval.end(), horizontal, normal, eye, color, pulse);
    }

    public void ray(Direction.Axis axis, double center, int side, double fixed, double start, double end,
                    boolean horizontal, int normal, Vec3 eye, int color, float pulse) {
        ray(axis, center, side, fixed, start, end, horizontal, normal, eye, color, pulse,
            PortalGlowOcclusion.RAY_ROOT_OFFSET);
    }

    public void ray(Direction.Axis axis, double center, int side, double fixed, double start, double end,
                    boolean horizontal, int normal, Vec3 eye, int color, float pulse, double rootOffset) {
        if (end <= start) return;
        double eyeCoordinate = horizontal ? eye.y : axis == Direction.Axis.X ? eye.x : eye.z;
        if ((eyeCoordinate - fixed) * normal <= PortalGlowGeometry.FACE_EPSILON) return;
        double u0 = horizontal ? start : fixed, y0 = horizontal ? fixed : start;
        double u1 = horizontal ? end : fixed, y1 = horizontal ? fixed : end;
        double root = center + side * (0.125 + rootOffset);
        double outer = center + side * (pulse > 0 ? 2 : 1);
        Vec3 outward = horizontal ? new Vec3(0, normal, 0)
            : axis == Direction.Axis.X ? new Vec3(normal, 0, 0) : new Vec3(0, 0, normal);
        Vec3 depth = axis == Direction.Axis.X ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
        Vec3 tangent = depth.cross(outward);
        Vec3 a = world(axis, u0, y0, root), b = world(axis, u1, y1, root);
        float phase = (float)(a.dot(tangent) % 1.5);
        float endPhase = phase + (float)b.subtract(a).dot(tangent);
        Vertex va = new Vertex(a.subtract(eye), phase, 0.125F);
        Vertex vb = new Vertex(b.subtract(eye), endPhase, 0.125F);
        Vertex vc = vertex(axis, u1, y1, outer, eye, endPhase, pulse > 0 ? 2 : 1);
        Vertex vd = vertex(axis, u0, y0, outer, eye, phase, pulse > 0 ? 2 : 1);
        boolean forward = b.subtract(a).cross(world(axis, u1, y1, outer).subtract(b)).dot(outward) > 0;
        quads.add(forward ? new Quad(PortalGlowRenderLayer.LAYER, va, vb, vc, vd, color)
            : new Quad(PortalGlowRenderLayer.LAYER, vd, vc, vb, va, color));
    }

    private static Vec3 world(Direction.Axis axis, double u, double y, double depth) {
        return axis == Direction.Axis.X ? new Vec3(u, y, depth) : new Vec3(depth, y, u);
    }
    private static Vertex vertex(Direction.Axis axis, double u, double y, double depth, Vec3 eye, float texU, float texV) {
        return new Vertex(world(axis, u, y, depth).subtract(eye), texU, texV);
    }
    @Override public Set<SingleQuadParticle.Layer> layers() {
        Set<SingleQuadParticle.Layer> result = new HashSet<>();
        for (var quad : quads) result.add(quad.layer);
        return result;
    }
    @Override public boolean isEmpty() { return quads.isEmpty(); }
    @Override public void clear() { quads.clear(); }
    @Override public void submit(SubmitNodeCollector collector, CameraRenderState camera) {
        if (!isEmpty()) collector.submitQuadParticleGroup(this);
    }
    @Override public void buildLayer(SingleQuadParticle.Layer layer, VertexConsumer buffer) {
        for (var q : quads) if (q.layer == layer) {
            emit(buffer, q.a, q.color); emit(buffer, q.b, q.color); emit(buffer, q.c, q.color); emit(buffer, q.d, q.color);
        }
    }
    private static void emit(VertexConsumer buffer, Vertex vertex, int color) {
        var p = vertex.position;
        buffer.addVertex((float)p.x, (float)p.y, (float)p.z).setUv(vertex.u, vertex.v).setColor(color).setLight(0xF000F0);
    }
}
