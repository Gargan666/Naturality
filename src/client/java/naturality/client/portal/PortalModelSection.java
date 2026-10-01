package naturality.client.portal;

import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Rasterizes an oriented mesh/plane intersection onto the portal's 16-pixel grid. */
public final class PortalModelSection {
    private record Segment(double x0, double y0, double x1, double y1) { }
    private record Event(double x, int winding) { }
    private final List<Segment> segments = new ArrayList<>();
    private final boolean xAxis;
    private final double plane;
    public PortalModelSection(boolean xAxis, double plane) { this.xAxis=xAxis; this.plane=plane; }
    private double depth(Vec3 p) { return (xAxis?p.z:p.x)-plane; }
    private double horizontal(Vec3 p) { return xAxis?p.x:p.z; }

    public void quad(Vec3[] vertices) {
        List<Vec3> hits = new ArrayList<>();
        for (int i=0;i<4;i++) {
            Vec3 a=vertices[i], b=vertices[(i+1)%4];
            double da=depth(a), db=depth(b);
            if ((da<0 && db>=0) || (db<0 && da>=0)) {
                Vec3 hit=a.add(b.subtract(a).scale(da/(da-db)));
                if(hits.stream().noneMatch(p->p.distanceToSqr(hit)<1e-12)) hits.add(hit);
            }
        }
        if(hits.size()!=2) return;
        Vec3 a=hits.get(0), b=hits.get(1);
        Vec3 normal=vertices[1].subtract(vertices[0]).cross(vertices[2].subtract(vertices[0]));
        Vec3 direction=normal.cross(xAxis?new Vec3(0,0,1):new Vec3(1,0,0));
        if(b.subtract(a).dot(direction)<0) {Vec3 temp=a;a=b;b=temp;}
        segments.add(new Segment(horizontal(a),a.y,horizontal(b),b.y));
    }

    public List<PortalGlowOcclusion.Rect> rectangles(double left,double bottom,double right,double top) {
        List<PortalGlowOcclusion.Rect> result=new ArrayList<>();
        Map<String,Integer> previous=new HashMap<>();
        for(int row=(int)Math.floor(bottom*16);row<(int)Math.ceil(top*16);row++) {
            double y=(row+0.5)/16;
            List<Event> events=new ArrayList<>();
            for(var s:segments) if(y>=Math.min(s.y0,s.y1) && y<Math.max(s.y0,s.y1)) {
                double x=s.x0+(y-s.y0)*(s.x1-s.x0)/(s.y1-s.y0);
                events.add(new Event(x,s.y1>s.y0?1:-1));
            }
            events.sort(Comparator.comparingDouble(Event::x));
            List<PortalGlowOcclusion.Interval> intervals=new ArrayList<>();
            int winding=0;double start=0;
            for(var e:events) {
                int old=winding;winding+=e.winding;
                if(old==0 && winding!=0) start=e.x;
                if(old!=0 && winding==0) {
                    double lo=Math.max(left,Math.floor(start*16)/16),hi=Math.min(right,Math.ceil(e.x*16)/16);
                    if(hi>lo) intervals.add(new PortalGlowOcclusion.Interval(lo,hi));
                }
            }
            // Adjacent model parts can quantize into the same pixel: merge those runs.
            List<PortalGlowOcclusion.Interval> merged=new ArrayList<>();
            for(var span:intervals) {
                if(!merged.isEmpty() && merged.getLast().end()>=span.start()) {
                    var last=merged.removeLast();merged.add(new PortalGlowOcclusion.Interval(last.start(),Math.max(last.end(),span.end())));
                } else merged.add(span);
            }
            Map<String,Integer> current=new HashMap<>();
            for(var span:merged) {
                String key=span.start()+":"+span.end();
                Integer index=previous.get(key);
                if(index==null) {index=result.size();result.add(new PortalGlowOcclusion.Rect(span.start(),row/16.0,span.end(),(row+1)/16.0));}
                else {var old=result.get(index);result.set(index,new PortalGlowOcclusion.Rect(old.left(),old.bottom(),old.right(),(row+1)/16.0));}
                current.put(key,index);
            }
            previous=current;
        }
        return result;
    }
}
