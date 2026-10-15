package naturality.client.particle;

import java.util.*;
import naturality.client.fluid.WaterIntersectionGeometry;
import naturality.client.portal.PortalGlowOcclusion;
import net.minecraft.world.phys.Vec3;

public final class WaterIntersectionGeometryTest {
    public static void run() {
        var mesh=new ArrayList<Vec3[]>();
        box(mesh,-1,0,-0.5,-0.75,1,0.5);
        box(mesh,0,0,-0.5,0.25,1,0.5);
        var legs=WaterIntersectionGeometry.section(mesh,0.5,0,0,-2,-2,2,2);
        check(legs.size()==2,"Separate model parts must keep their gap");
        check(legs.stream().noneMatch(r->r.contains(-0.4,0)),"A bounding-box rim would fill this gap");
        check(WaterIntersectionGeometry.section(mesh,2,0,0,-2,-2,2,2).isEmpty(),"No intersection above model");
        check(WaterIntersectionGeometry.section(mesh,-1,0,0,-2,-2,2,2).isEmpty(),"No intersection below model");
        check(WaterIntersectionGeometry.section(mesh,0.5,0.25,0.125,-2,-2,2,2).size()==2,"Sloping water must cut the real mesh");
        for(var r:legs) for(double v:new double[]{r.left(),r.bottom(),r.right(),r.top()})
            check(v*16==Math.rint(v*16),"Negative-coordinate sections must align to the water grid");
        var rect=new PortalGlowOcclusion.Rect(0,0,1,1);
        var union=List.of(rect,new PortalGlowOcclusion.Rect(0.5,0,1.5,1));
        check(WaterIntersectionGeometry.alpha(union,0.99,0.5)==0,"No internal overlap seam");
        var adjacent=List.of(rect,new PortalGlowOcclusion.Rect(1,0,2,1));
        check(WaterIntersectionGeometry.alpha(adjacent,1-0.5/16,0.5)==0,"Inset must not reveal adjacent rectangle seams");
        float near=WaterIntersectionGeometry.alpha(List.of(rect),1-0.5/16,0.5);
        float middle=WaterIntersectionGeometry.alpha(List.of(rect),1+0.5/16,0.5);
        float far=WaterIntersectionGeometry.alpha(List.of(rect),1+1.5/16,0.5);
        check(near>middle && middle>far && far>0 && near<1,"Rim must fade through transparent pixel rows");
        check(WaterIntersectionGeometry.alpha(List.of(rect),1+2.5/16,0.5)==0,"Outer reach must shrink by one pixel");
        check(WaterIntersectionGeometry.alpha(List.of(rect),1-1.5/16,0.5)==0,"Inset must not fill the model interior");
        check(WaterIntersectionGeometry.alpha(List.of(rect,rect),1-0.5/16,0.5)==near,"Overlaps must not double opacity");
        check(naturality.client.fluid.WaterRipples.coverage(0,0,3)==0,"Rain ring must have an empty center");
        check(naturality.client.fluid.WaterRipples.coverage(3,0,3)==0.65F,"Rain ring core pixel");
        check(naturality.client.fluid.WaterRipples.coverage(3,2,3)==0.08F,"Faint corner pixels soften the rain circle");
        check(naturality.client.fluid.WaterRipples.coverage(5,5,3)==0,"No distant rain pixels");
        check(naturality.client.fluid.WaterRipples.coverage(3,3,4)==0.08F,"Circle elbow must only be a faint shoulder");
        check(naturality.client.fluid.WaterRipples.coverage(3,2,4)==0.65F
            && naturality.client.fluid.WaterRipples.coverage(2,3,4)==0.65F,"Basic diagonal circle line must remain");
        System.out.println("Water intersection checks passed: posed-part gaps, slopes, negative grid, overlap and alpha fade.");
    }
    private static void box(List<Vec3[]> mesh,double x0,double y0,double z0,double x1,double y1,double z1) {
        Vec3 a=new Vec3(x0,y0,z0),b=new Vec3(x1,y0,z0),c=new Vec3(x1,y1,z0),d=new Vec3(x0,y1,z0);
        Vec3 e=new Vec3(x0,y0,z1),f=new Vec3(x1,y0,z1),g=new Vec3(x1,y1,z1),h=new Vec3(x0,y1,z1);
        mesh.add(new Vec3[]{a,e,h,d});mesh.add(new Vec3[]{f,b,c,g});
        mesh.add(new Vec3[]{a,b,f,e});mesh.add(new Vec3[]{h,g,c,d});
        mesh.add(new Vec3[]{b,a,d,c});mesh.add(new Vec3[]{e,f,g,h});
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
