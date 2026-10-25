package naturality.client.portals.nether.travel;

import naturality.client.api.geometry.PlanarGeometry.ModelSection;
import naturality.client.api.geometry.PlanarGeometry;
import naturality.client.api.geometry.ModelCapture;
import naturality.portals.nether.travel.PortalCrossing;
import naturality.portals.nether.travel.PortalCrossing.PortalCrossingPayload;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.*;
import net.minecraft.client.renderer.*;
import org.jspecify.annotations.Nullable;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import naturality.client.portals.nether.glow.PortalGlowController;
import naturality.client.portals.nether.glow.PortalGlowGeometry;
import naturality.client.portals.nether.glow.PortalGlowGeometry.PortalGlowRenderLayer;
import naturality.client.portals.nether.opening.PortalOpeningClient;
import naturality.Naturality;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.*;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

@org.jspecify.annotations.NullMarked
public final class PortalCrossingClient {
    public static @org.jspecify.annotations.Nullable List<PlanarGeometry.Rect> captureModel(
            Entity entity, PortalCrossing crossing, Camera camera, float partialTick) {
        var origin = Vec3.atLowerCornerOf(crossing.anchor);
        boolean x = crossing.axis == net.minecraft.core.Direction.Axis.X;
        var section = new ModelSection(x, crossing.plane);
        return ModelCapture.capture(entity, origin, section,
            x ? crossing.min.getX() : crossing.min.getZ(), crossing.min.getY(),
            (x ? crossing.max.getX() : crossing.max.getZ()) + 1, crossing.max.getY() + 1,
            camera, partialTick);
    }

    private record Blocker(PortalCrossing crossing, PlanarGeometry.Rect rect, net.minecraft.world.phys.AABB box, float strength) { }
    private static final List<Blocker> BLOCKERS = new ArrayList<>();
    private static @org.jspecify.annotations.Nullable PortalEntityGlowState glowState;

    public static void beginGlowFrame(Camera camera, float partialTick) {
        BLOCKERS.clear();
        glowState = new PortalEntityGlowState();
        var client = Minecraft.getInstance();
        var level = client.level;
        if (level == null || !naturality.config.live.NaturalityConfig.get().portalChanges.glowEffect
            || client.options.particles().get() == net.minecraft.server.level.ParticleStatus.MINIMAL) return;
        for (var entity : level.entitiesForRendering()) {
            var crossing = PortalCrossing.get(entity);
            if (crossing == null || !crossing.valid(entity) || crossing.signed(camera.position().x, camera.position().z) < 0) continue;
            var box = entity.getBoundingBox().move(entity.getPosition(partialTick).subtract(entity.position()));
            float progress = crossing.progress(box);
            if (progress <= 0 || progress >= 1) continue;
            boolean x = crossing.axis == net.minecraft.core.Direction.Axis.X;
            var captured = naturality.client.portals.nether.travel.PortalCrossingClient.captureModel(entity, crossing, camera, partialTick);
            var sections = captured != null ? captured : List.of(PlanarGeometry.pixels(x ? box.minX : box.minZ, box.minY, x ? box.maxX : box.maxZ, box.maxY));
            for (var rect : sections)
                BLOCKERS.add(new Blocker(crossing, rect, box, (float)Math.sin(Math.PI * progress)));
        }
    }

    /** Replaces only affected frame tiles; ordinary frame particles keep their cheap rendering path. */
    public static boolean clipFrame(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction.Axis axis,
            net.minecraft.core.Direction inward, double x, double y, double z, int side, Vec3 eye, int color, float pulse,
            double trimStart, double trimEnd) {
        var glowState = PortalCrossingClient.glowState;
        if (glowState == null || BLOCKERS.isEmpty() && trimStart == 0 && trimEnd == 0) return false;
        boolean horizontal = inward.getAxis() == net.minecraft.core.Direction.Axis.Y;
        double center = (axis == net.minecraft.core.Direction.Axis.X ? pos.getZ() : pos.getX()) + 0.5;
        double fixed = horizontal ? y : axis == net.minecraft.core.Direction.Axis.X ? x : z;
        double middle = horizontal ? (axis == net.minecraft.core.Direction.Axis.X ? x : z) : y;
        double start = middle - 0.5 + trimStart, end = middle + 0.5 - trimEnd;
        List<PlanarGeometry.Interval> cuts = new ArrayList<>();
        for (var blocker : BLOCKERS) {
            var c = blocker.crossing;
            if (c.axis != axis || c.side != side || Math.abs(c.plane - side * 0.125 - center) > 0.001) continue;
            var r = blocker.rect;
            boolean covered = horizontal ? fixed >= r.bottom() - 0.002 && fixed <= r.top() + 0.002
                : fixed >= r.left() - 0.002 && fixed <= r.right() + 0.002;
            if (!covered) continue;
            var cut = horizontal ? new PlanarGeometry.Interval(r.left(), r.right()) : new PlanarGeometry.Interval(r.bottom(), r.top());
            if (cut.end() > start && cut.start() < end) cuts.add(cut);
        }
        if (cuts.isEmpty() && trimStart == 0 && trimEnd == 0) return false;
        int normal = horizontal ? inward.getStepY() : axis == net.minecraft.core.Direction.Axis.X ? inward.getStepX() : inward.getStepZ();
        for (var interval : PlanarGeometry.uncovered(start, end, cuts))
            // Frame rays touch the portal slab. Only entity rays need the
            // extra clearance from their separate silhouette halo surface.
            glowState.ray(axis, center, side, fixed, interval.start(), interval.end(), horizontal, normal, eye, color, pulse, 0);
        return true;
    }
    private record Side(net.minecraft.core.Direction.Axis axis,double plane,int side) {
        boolean hidden(Vec3 eye){return ((axis==net.minecraft.core.Direction.Axis.X?eye.z:eye.x)-plane)*side<0;}
    }
    private static final Map<EntityRenderState,Side> SIDES=Collections.synchronizedMap(new WeakHashMap<>());
    public static void initialize(){
        PortalEntityGlowState.initialize();
        if (naturality.config.startup.FeatureConfig.clientOnly()) return;
        ClientPlayNetworking.registerGlobalReceiver(PortalCrossingPayload.TYPE,(p,context)->{
            var level=context.client().level;
            if(level==null || !level.dimension().identifier().toString().equals(p.dimension())) return;
            var entity=level.getEntity(p.entity());
            if(entity==null) return;
            if(p.active() && !naturality.config.gameplay.GameplaySettings.clientPhysicalPortalEntry()) return;
            if(p.active()) entity.setPortalCooldown(0);
            var existing=PortalCrossing.get(entity);
            if(!p.active()) PortalCrossing.set(entity,null);
            else if(existing==null || !existing.anchor.equals(p.pos()) || existing.side!=p.side())
                PortalCrossing.set(entity,new PortalCrossing(entity,p.pos(),p.axis(),p.side()));
        });
    }
    public static void remember(Entity entity,EntityRenderState state){
        var crossing=PortalCrossing.get(entity);
        if(crossing==null || !crossing.valid(entity)) SIDES.remove(state);
        else SIDES.put(state,new Side(crossing.axis,crossing.plane,crossing.side));
    }
    public static boolean hidden(EntityRenderState state,Vec3 camera){
        var side=SIDES.get(state);return side!=null && side.hidden(camera);
    }
    public static net.minecraft.client.renderer.SubmitNodeCollector clip(EntityRenderState state,Vec3 camera,
            net.minecraft.client.renderer.SubmitNodeCollector collector) {
        var side=SIDES.get(state);
        if(side==null || ModelCapture.capturing) return collector;
        boolean x=side.axis==net.minecraft.core.Direction.Axis.X;
        return PortalClippedCollector.wrap(collector,x,side.plane-(x?camera.z:camera.x),side.side);
    }
    public static void extract(ParticlesRenderState output,Frustum frustum,Camera camera,float partialTick){
        var client=Minecraft.getInstance();
        var level = client.level;
        if(level==null || !naturality.config.live.NaturalityConfig.get().portalChanges.glowEffect) return;
        var glowState = PortalCrossingClient.glowState;
        if (glowState == null) return;
        for (var blocker : BLOCKERS) {
            if (!frustum.isVisible(blocker.box.inflate(1.5))) continue;
            List<PlanarGeometry.Rect> neighbors = new ArrayList<>();
            for (var other : BLOCKERS) if (other != blocker && other.crossing.axis == blocker.crossing.axis
                && other.crossing.side == blocker.crossing.side && Math.abs(other.crossing.plane - blocker.crossing.plane) < 0.001)
                neighbors.add(other.rect);
            glowState.add(blocker.crossing, blocker.rect, camera.position(), blocker.strength, neighbors);
        }
        if(!glowState.isEmpty()) output.add(glowState);
    }

    /** Carry a plane from entity extraction into deferred feature submissions. */
    public static final class PortalClippedCollector {
        public record Plane(boolean xAxis,double coordinate,int side) {
            public PortalClippedVertexConsumer wrap(com.mojang.blaze3d.vertex.VertexConsumer buffer) {
                return new PortalClippedVertexConsumer(buffer,xAxis,coordinate,side);
            }
        }
        private static @org.jspecify.annotations.Nullable Plane current;
        public static void remember(Object submit) {
            if (submit instanceof PortalClippedSubmit clipped) clipped.naturality$clipPlane(current);
        }
        public static @org.jspecify.annotations.Nullable Plane plane(Object submit) {
            return submit instanceof PortalClippedSubmit clipped ? clipped.naturality$clipPlane() : null;
        }
        public static SubmitNodeCollector wrap(SubmitNodeCollector target,boolean xAxis,double plane,int side) {
            return (SubmitNodeCollector)proxy(target,SubmitNodeCollector.class,new Plane(xAxis,plane,side));
        }
        private static Object proxy(OrderedSubmitNodeCollector target,Class<?> type,Plane plane) {
            return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)-> {
                if(method.getName().equals("order")) return proxy(((SubmitNodeCollector)target).order((int)args[0]),OrderedSubmitNodeCollector.class,plane);
                if(method.isDefault()) return InvocationHandler.invokeDefault(proxy,method,args);
                if(method.getName().equals("submitCustomGeometry")) {
                    var original=(SubmitNodeCollector.CustomGeometryRenderer)args[2];
                    args[2]=(SubmitNodeCollector.CustomGeometryRenderer)(pose,buffer)-> {
                        var clipped=plane.wrap(buffer);original.render(pose,clipped);clipped.finish();
                    };
                }
                Plane previous=current;current=plane;
                try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}
                finally {current=previous;}
            });
        }
        private PortalClippedCollector(){}
    }

    /** Clip metadata belongs to this submission, never to its structural record value. */
    public interface PortalClippedSubmit {
        PortalClippedCollector.@Nullable Plane naturality$clipPlane();
        void naturality$clipPlane(PortalClippedCollector.@Nullable Plane plane);
    }

    /** Clips textured quads in camera-relative space, interpolating every vertex attribute. */
    public static final class PortalClippedVertexConsumer implements VertexConsumer {
        private final VertexConsumer output;
        private final boolean xAxis;
        private final double plane;
        private final int side;
        private final List<float[]> quad=new ArrayList<>(4);
        private float[] vertex = new float[19];
        public PortalClippedVertexConsumer(VertexConsumer output,boolean xAxis,double plane,int side) {
            this.output=output;this.xAxis=xAxis;this.plane=plane;this.side=side;
        }
        private double distance(float[] v) {return (v[xAxis?2:0]-plane)*side;}
        private float[] interpolate(float[] a,float[] b,double t) {
            float[] v=new float[a.length];
            for(int i=0;i<v.length;i++) v[i]=(float)(a[i]+(b[i]-a[i])*t);
            return v;
        }
        private void flush() {
            if(quad.isEmpty()) return;
            List<float[]> polygon=new ArrayList<>(6);
            float[] a=quad.getLast();double da=distance(a);
            for(float[] b:quad) {
                double db=distance(b);
                if((da>=0)!=(db>=0)) polygon.add(interpolate(a,b,da/(da-db)));
                if(db>=0) polygon.add(b);
                a=b;da=db;
            }
            if(polygon.size()==4) for(float[] v:polygon) emit(v);
            else for(int i=1;i+1<polygon.size();i++) {
                emit(polygon.getFirst());emit(polygon.get(i));emit(polygon.get(i+1));emit(polygon.get(i+1));
            }
            quad.clear();
        }
        private void emit(float[] v) {
            output.addVertex(v[0],v[1],v[2]).setColor(Math.round(v[3]),Math.round(v[4]),Math.round(v[5]),Math.round(v[6]))
                .setUv(v[7],v[8]).setUv1(Math.round(v[9]),Math.round(v[10])).setUv2(Math.round(v[11]),Math.round(v[12]))
                .setNormal(v[13],v[14],v[15]).setUv3(v[16],v[17]).setLineWidth(v[18]);
        }
        /** The final vertex is complete only after its attributes have been written. */
        public void finish(){flush();}
        @Override public VertexConsumer addVertex(float x,float y,float z) {
            if(quad.size()==4) flush();
            vertex=new float[]{x,y,z,255,255,255,255,0,0,0,0,0,0,0,0,0,0,0,1};
            quad.add(vertex);return this;
        }
        @Override public VertexConsumer setColor(int r,int g,int b,int a){vertex[3]=r;vertex[4]=g;vertex[5]=b;vertex[6]=a;return this;}
        @Override public VertexConsumer setColor(int c){return setColor(c>>16&255,c>>8&255,c&255,c>>>24);}
        @Override public VertexConsumer setUv(float u,float v){vertex[7]=u;vertex[8]=v;return this;}
        @Override public VertexConsumer setUv1(int u,int v){vertex[9]=u;vertex[10]=v;return this;}
        @Override public VertexConsumer setUv2(int u,int v){vertex[11]=u;vertex[12]=v;return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){vertex[13]=x;vertex[14]=y;vertex[15]=z;return this;}
        @Override public VertexConsumer setUv3(float u,float v){vertex[16]=u;vertex[17]=v;return this;}
        @Override public VertexConsumer setLineWidth(float width){vertex[18]=width;return this;}
    }

    /** Shared geometry for entity silhouettes and frame edges clipped by those silhouettes. */
    public static final class PortalEntityGlowState extends QuadParticleRenderState {
        private static final SingleQuadParticle.Layer SURFACE = PortalEntityGlowLayer.SURFACE;
        private record Vertex(Vec3 position, float u, float v) { }
        private record Quad(SingleQuadParticle.Layer layer, Vertex a, Vertex b, Vertex c, Vertex d, int color) { }
        private final List<Quad> quads = new ArrayList<>();
        public static void initialize() { }

        public void add(PortalCrossing crossing, PlanarGeometry.Rect rect, Vec3 eye, float strength,
                        List<PlanarGeometry.Rect> blockers) {
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
                double distance = PlanarGeometry.distance(rect, midU, midY);
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
                    double otherDistance = PlanarGeometry.distance(other, midU, midY);
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
                double plane = crossing.plane + crossing.side * naturality.client.portals.nether.glow.PortalGlowGeometry.HALO_OFFSET;
                quads.add(new Quad(SURFACE,
                    vertex(crossing.axis, u / 16.0, y / 16.0, plane, eye, row, reach / 16F),
                    vertex(crossing.axis, (u + 1) / 16.0, y / 16.0, plane, eye, row, reach / 16F),
                    vertex(crossing.axis, (u + 1) / 16.0, (y + 1) / 16.0, plane, eye, row, reach / 16F),
                    vertex(crossing.axis, u / 16.0, (y + 1) / 16.0, plane, eye, row, reach / 16F), color));
            }
        }

        private void edge(Direction.Axis axis, double center, int side, double fixed, double start, double end,
                          boolean horizontal, int normal, List<PlanarGeometry.Rect> blockers, Vec3 eye, int color, float pulse) {
            List<PlanarGeometry.Interval> cuts = new ArrayList<>();
            double sample = fixed + normal / 1024.0;
            for (var other : blockers) {
                if (horizontal ? sample > other.bottom() && sample < other.top() : sample > other.left() && sample < other.right())
                    cuts.add(horizontal ? new PlanarGeometry.Interval(other.left(), other.right())
                        : new PlanarGeometry.Interval(other.bottom(), other.top()));
            }
            for (var interval : PlanarGeometry.uncovered(start, end, cuts))
                ray(axis, center, side, fixed + normal * PortalGlowGeometry.INSET, interval.start(), interval.end(), horizontal, normal, eye, color, pulse);
        }

        public void ray(Direction.Axis axis, double center, int side, double fixed, double start, double end,
                        boolean horizontal, int normal, Vec3 eye, int color, float pulse) {
            ray(axis, center, side, fixed, start, end, horizontal, normal, eye, color, pulse,
                naturality.client.portals.nether.glow.PortalGlowGeometry.RAY_ROOT_OFFSET);
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

    public static final class PortalOverlay {
        public static final RenderPipeline PIPELINE=RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Naturality.id("pipeline/portal_overlay")).withFragmentShader(Naturality.id("core/portal_overlay")).build());
        public static void initialize(){}

        public static float cameraOpacity(float previous) {
            var client=net.minecraft.client.Minecraft.getInstance();
            var player = client.player;
            if(player==null) return previous;
            var crossing=naturality.portals.nether.travel.PortalCrossing.get(player);
            if(crossing==null || !crossing.valid(player)) return previous;
            var camera=client.gameRenderer.mainCamera();
            var eye=camera.position();
            var near=camera.getNearPlane(camera.getFov());
            double closest=Double.POSITIVE_INFINITY;
            for(var offset:java.util.List.of(near.getTopLeft(),near.getTopRight(),near.getBottomLeft(),near.getBottomRight())) {
                var corner=eye.add(offset);
                closest=Math.min(closest,crossing.signed(corner.x,corner.z));
            }
            // Saturate before even a near-plane corner reaches the portal face.
            if(closest<=1.0/64) return 1;
            double half=player.getBbWidth()/2.0;
            double distance=crossing.signed(eye.x,eye.z);
            double margin=Math.max(0,distance-closest)+1.0/64;
            return Math.max(previous,(float)Math.clamp((half-distance)/Math.max(0.001,half-margin),0,1));
        }
    }
}
