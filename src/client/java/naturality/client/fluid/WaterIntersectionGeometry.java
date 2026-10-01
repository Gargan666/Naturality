package naturality.client.fluid;

import java.util.*;
import naturality.client.portal.*;
import net.minecraft.world.phys.Vec3;

/** Geometry-only water cross sections and a non-overlapping, three-pixel square-distance rim. */
public final class WaterIntersectionGeometry {
    private WaterIntersectionGeometry() {}
    public static List<PortalGlowOcclusion.Rect> section(List<Vec3[]> mesh, double height, double dx, double dz,
                                                         double left, double bottom, double right, double top) {
        var section = new PortalModelSection(true, height);
        for (var quad : mesh) {
            var transformed = new Vec3[4];
            for (int i=0;i<4;i++) {
                var p=quad[i];
                transformed[i]=new Vec3(p.x,p.z,p.y-dx*p.x-dz*p.z);
            }
            section.quad(transformed);
        }
        return section.rectangles(left,bottom,right,top);
    }
    public static float alpha(List<PortalGlowOcclusion.Rect> sections, double x, double z) {
        double distance=Double.POSITIVE_INFINITY;
        for (var rect:sections) distance=Math.min(distance,PortalGlowOcclusion.distance(rect,x,z));
        // Move the whole gradient inward one water texel. The inner row belongs
        // only to the union's exposed boundary, never a join between rectangles.
        if (distance<=0) {
            boolean boundary=false;
            for (int dx=-1;dx<=1;dx++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0 && dz==0) continue;
                boolean occupied=false;
                for (var rect:sections) if (rect.contains(x+dx/16.0,z+dz/16.0)) {
                    occupied=true;
                    break;
                }
                if (!occupied) boundary=true;
            }
            if (!boundary) return 0;
        }
        distance+=1.0/16;
        if (distance<=0 || distance>=3.0/16) return 0;
        return switch ((int)Math.floor(distance*16)) { case 0 -> 0.72F; case 1 -> 0.34F; default -> 0.10F; };
    }
}
