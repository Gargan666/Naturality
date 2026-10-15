package naturality.client.api.geometry;

import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Geometry in portal coordinates: horizontal position and height, in block units. */
@org.jspecify.annotations.NullMarked
public final class PlanarGeometry {
    public record Rect(double left, double bottom, double right, double top) {
        public boolean contains(double u, double y) {
            return u > left && u < right && y > bottom && y < top;
        }
    }
    public record Interval(double start, double end) { }

    /** Subtract the union of blockers, retaining sub-pixel endpoints without gaps or overlaps. */
    public static List<Interval> uncovered(double start, double end, List<Interval> blockers) {
        List<Interval> sorted = new ArrayList<>(blockers);
        sorted.sort(Comparator.comparingDouble(Interval::start));
        List<Interval> result = new ArrayList<>();
        double cursor = start;
        for (Interval block : sorted) {
            if (block.end <= cursor || block.start >= end) continue;
            if (block.start > cursor) result.add(new Interval(cursor, Math.min(end, block.start)));
            cursor = Math.max(cursor, block.end);
            if (cursor >= end) break;
        }
        if (cursor < end) result.add(new Interval(cursor, end));
        return result;
    }

    public static Rect pixels(double left, double bottom, double right, double top) {
        return new Rect(Math.floor(left * 16) / 16, Math.floor(bottom * 16) / 16,
            Math.ceil(right * 16) / 16, Math.ceil(top * 16) / 16);
    }

    public static double distance(Rect rect, double u, double y) {
        return Math.max(Math.max(rect.left - u, u - rect.right), Math.max(rect.bottom - y, y - rect.top));
    }
    private PlanarGeometry() { }

    /** Rasterizes an oriented mesh/plane intersection onto the portal's 16-pixel grid. */
    public static final class ModelSection {
        private record Segment(double x0, double y0, double x1, double y1) { }
        private record Event(double x, int winding) { }
        private final List<Segment> segments = new ArrayList<>();
        private final boolean xAxis;
        private final double plane;
        public ModelSection(boolean xAxis, double plane) { this.xAxis=xAxis; this.plane=plane; }
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

        public List<PlanarGeometry.Rect> rectangles(double left,double bottom,double right,double top) {
            List<PlanarGeometry.Rect> result=new ArrayList<>();
            Map<String,Integer> previous=new HashMap<>();
            for(int row=(int)Math.floor(bottom*16);row<(int)Math.ceil(top*16);row++) {
                double y=(row+0.5)/16;
                List<Event> events=new ArrayList<>();
                for(var s:segments) if(y>=Math.min(s.y0,s.y1) && y<Math.max(s.y0,s.y1)) {
                    double x=s.x0+(y-s.y0)*(s.x1-s.x0)/(s.y1-s.y0);
                    events.add(new Event(x,s.y1>s.y0?1:-1));
                }
                events.sort(Comparator.comparingDouble(Event::x));
                List<PlanarGeometry.Interval> intervals=new ArrayList<>();
                int winding=0;double start=0;
                for(var e:events) {
                    int old=winding;winding+=e.winding;
                    if(old==0 && winding!=0) start=e.x;
                    if(old!=0 && winding==0) {
                        double lo=Math.max(left,Math.floor(start*16)/16),hi=Math.min(right,Math.ceil(e.x*16)/16);
                        if(hi>lo) intervals.add(new PlanarGeometry.Interval(lo,hi));
                    }
                }
                // Adjacent model parts can quantize into the same pixel: merge those runs.
                List<PlanarGeometry.Interval> merged=new ArrayList<>();
                for(var span:intervals) {
                    if(!merged.isEmpty() && merged.getLast().end()>=span.start()) {
                        var last=merged.removeLast();merged.add(new PlanarGeometry.Interval(last.start(),Math.max(last.end(),span.end())));
                    } else merged.add(span);
                }
                Map<String,Integer> current=new HashMap<>();
                for(var span:merged) {
                    String key=span.start()+":"+span.end();
                    Integer index=previous.get(key);
                    if(index==null) {index=result.size();result.add(new PlanarGeometry.Rect(span.start(),row/16.0,span.end(),(row+1)/16.0));}
                    else {var old=result.get(index);result.set(index,new PlanarGeometry.Rect(old.left(),old.bottom(),old.right(),(row+1)/16.0));}
                    current.put(key,index);
                }
                previous=current;
            }
            return result;
        }
    }
}
