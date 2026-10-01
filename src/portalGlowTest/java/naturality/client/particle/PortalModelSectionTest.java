package naturality.client.particle;
import naturality.client.portal.PortalModelSection;
import net.minecraft.world.phys.Vec3;

public final class PortalModelSectionTest {
    public static void run() {
        var section=new PortalModelSection(true,0);
        box(section,0,0,0.25,1);
        box(section,0.5,0,0.75,1);
        var legs=section.rectangles(-1,-1,2,2);
        check(legs.size()==2,"Separate legs must leave a light gap between them");
        check(legs.stream().noneMatch(r->r.contains(0.375,0.5)),"Model gaps must not inherit the hitbox shadow");
        box(section,0.125,0,0.625,1);
        var joined=section.rectangles(-1,-1,2,2);
        check(joined.size()==1 && joined.getFirst().left()==0 && joined.getFirst().right()==0.75,
            "Overlapping model parts must form a union, not cancel each other");
        var absent=new PortalModelSection(true,2);
        box(absent,0,0,1,1);
        check(absent.rectangles(-1,-1,2,2).isEmpty(),"A model outside the portal plane must cast no intersection glow");
        System.out.println("Portal model section checks passed: body-part gaps, overlap union and plane intersection.");
    }
    private static void box(PortalModelSection s,double x0,double y0,double x1,double y1) {
        Vec3 a=new Vec3(x0,y0,-0.5),b=new Vec3(x1,y0,-0.5),c=new Vec3(x1,y1,-0.5),d=new Vec3(x0,y1,-0.5);
        Vec3 e=new Vec3(x0,y0,0.5),f=new Vec3(x1,y0,0.5),g=new Vec3(x1,y1,0.5),h=new Vec3(x0,y1,0.5);
        s.quad(new Vec3[]{a,e,h,d});s.quad(new Vec3[]{f,b,c,g});
        s.quad(new Vec3[]{a,b,f,e});s.quad(new Vec3[]{h,g,c,d});
        s.quad(new Vec3[]{b,a,d,c});s.quad(new Vec3[]{e,f,g,h});
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
