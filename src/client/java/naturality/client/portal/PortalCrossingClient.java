package naturality.client.portal;

import java.util.*;
import naturality.portal.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class PortalCrossingClient {
    private record Blocker(PortalCrossing crossing, PortalGlowOcclusion.Rect rect, net.minecraft.world.phys.AABB box, float strength) { }
    private static final List<Blocker> BLOCKERS = new ArrayList<>();
    private static @org.jspecify.annotations.Nullable PortalEntityGlowState glowState;

    public static void beginGlowFrame(Camera camera, float partialTick) {
        BLOCKERS.clear();
        glowState = new PortalEntityGlowState();
        var client = Minecraft.getInstance();
        var level = client.level;
        if (level == null || !naturality.config.NaturalityConfig.get().portalChanges.glowEffect
            || client.options.particles().get() == net.minecraft.server.level.ParticleStatus.MINIMAL) return;
        for (var entity : level.entitiesForRendering()) {
            var crossing = PortalCrossing.get(entity);
            if (crossing == null || !crossing.valid(entity) || crossing.signed(camera.position().x, camera.position().z) < 0) continue;
            var box = entity.getBoundingBox().move(entity.getPosition(partialTick).subtract(entity.position()));
            float progress = crossing.progress(box);
            if (progress <= 0 || progress >= 1) continue;
            boolean x = crossing.axis == net.minecraft.core.Direction.Axis.X;
            var captured = PortalModelCapture.capture(entity, crossing, camera, partialTick);
            var sections = captured != null ? captured : List.of(PortalGlowOcclusion.pixels(x ? box.minX : box.minZ, box.minY, x ? box.maxX : box.maxZ, box.maxY));
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
        List<PortalGlowOcclusion.Interval> cuts = new ArrayList<>();
        for (var blocker : BLOCKERS) {
            var c = blocker.crossing;
            if (c.axis != axis || c.side != side || Math.abs(c.plane - side * 0.125 - center) > 0.001) continue;
            var r = blocker.rect;
            boolean covered = horizontal ? fixed >= r.bottom() - 0.002 && fixed <= r.top() + 0.002
                : fixed >= r.left() - 0.002 && fixed <= r.right() + 0.002;
            if (!covered) continue;
            var cut = horizontal ? new PortalGlowOcclusion.Interval(r.left(), r.right()) : new PortalGlowOcclusion.Interval(r.bottom(), r.top());
            if (cut.end() > start && cut.start() < end) cuts.add(cut);
        }
        if (cuts.isEmpty() && trimStart == 0 && trimEnd == 0) return false;
        int normal = horizontal ? inward.getStepY() : axis == net.minecraft.core.Direction.Axis.X ? inward.getStepX() : inward.getStepZ();
        for (var interval : PortalGlowOcclusion.uncovered(start, end, cuts))
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
        ClientPlayNetworking.registerGlobalReceiver(PortalCrossingPayload.TYPE,(p,context)->{
            var level=context.client().level;
            if(level==null || !level.dimension().identifier().toString().equals(p.dimension())) return;
            var entity=level.getEntity(p.entity());
            if(entity==null) return;
            if(p.active() && !naturality.config.GameplaySettings.clientPhysicalPortalEntry()) return;
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
        if(side==null || PortalModelCapture.capturing) return collector;
        boolean x=side.axis==net.minecraft.core.Direction.Axis.X;
        return PortalClippedCollector.wrap(collector,x,side.plane-(x?camera.z:camera.x),side.side);
    }
    public static void extract(ParticlesRenderState output,Frustum frustum,Camera camera,float partialTick){
        var client=Minecraft.getInstance();
        var level = client.level;
        if(level==null || !naturality.config.NaturalityConfig.get().portalChanges.glowEffect) return;
        var glowState = PortalCrossingClient.glowState;
        if (glowState == null) return;
        for (var blocker : BLOCKERS) {
            if (!frustum.isVisible(blocker.box.inflate(1.5))) continue;
            List<PortalGlowOcclusion.Rect> neighbors = new ArrayList<>();
            for (var other : BLOCKERS) if (other != blocker && other.crossing.axis == blocker.crossing.axis
                && other.crossing.side == blocker.crossing.side && Math.abs(other.crossing.plane - blocker.crossing.plane) < 0.001)
                neighbors.add(other.rect);
            glowState.add(blocker.crossing, blocker.rect, camera.position(), blocker.strength, neighbors);
        }
        if(!glowState.isEmpty()) output.add(glowState);
    }
}
