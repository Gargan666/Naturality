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
        private final boolean horizontal;
        private final double slopeX, slopeZ;
        // A section belongs to one extraction. Scratch never escapes into retained segments.
        private final double[] coordinates = new double[12], hits = new double[6];
        public ModelSection(boolean xAxis, double plane) { this(xAxis,plane,false,0,0); }
        private ModelSection(boolean xAxis,double plane,boolean horizontal,double slopeX,double slopeZ) {
            this.xAxis=xAxis;this.plane=plane;this.horizontal=horizontal;this.slopeX=slopeX;this.slopeZ=slopeZ;
        }
        /** Projects a sloped horizontal plane into X/Z without allocating a transformed mesh. */
        public static ModelSection horizontal(double height,double slopeX,double slopeZ) {
            return new ModelSection(true,height,true,slopeX,slopeZ);
        }

        public void quad(Vec3[] vertices) {
            for(int i=0;i<4;i++) {
                var p=vertices[i];int offset=i*3;
                coordinates[offset]=p.x;
                coordinates[offset+1]=horizontal?p.z:p.y;
                coordinates[offset+2]=horizontal?p.y-slopeX*p.x-slopeZ*p.z:p.z;
            }
            int count=0,depth=xAxis?2:0;
            for (int i=0;i<4;i++) {
                int a=i*3,b=((i+1)%4)*3;
                double da=coordinates[a+depth]-plane, db=coordinates[b+depth]-plane;
                if ((da<0 && db>=0) || (db<0 && da>=0)) {
                    double fraction=da/(da-db);
                    // Vec3.subtract adds the negated coordinate; retain its arithmetic,
                    // including signed zero, before interpolation and winding tests.
                    double x=coordinates[a]+(coordinates[b]+-coordinates[a])*fraction;
                    double y=coordinates[a+1]+(coordinates[b+1]+-coordinates[a+1])*fraction;
                    double z=coordinates[a+2]+(coordinates[b+2]+-coordinates[a+2])*fraction;
                    boolean duplicate=false;
                    for(int hit=0;hit<count;hit++) {
                        int offset=hit*3;
                        double dx=x-hits[offset],dy=y-hits[offset+1],dz=z-hits[offset+2];
                        if(dx*dx+dy*dy+dz*dz<1e-12) {duplicate=true;break;}
                    }
                    if(!duplicate) {
                        // Three distinct hits cannot become a valid two-hit segment.
                        if(count==2)return;
                        int offset=count++*3;
                        hits[offset]=x;hits[offset+1]=y;hits[offset+2]=z;
                    }
                }
            }
            if(count!=2) return;
            double ux=coordinates[3]+-coordinates[0],uy=coordinates[4]+-coordinates[1],uz=coordinates[5]+-coordinates[2];
            double vx=coordinates[6]+-coordinates[0],vy=coordinates[7]+-coordinates[1],vz=coordinates[8]+-coordinates[2];
            double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            double axisX=xAxis?0:1,axisZ=xAxis?1:0;
            double directionX=ny*axisZ-nz*0.0,directionY=nz*axisX-nx*axisZ,directionZ=nx*0.0-ny*axisX;
            double dot=(hits[3]+-hits[0])*directionX+(hits[4]+-hits[1])*directionY+(hits[5]+-hits[2])*directionZ;
            int a=dot<0?3:0,b=dot<0?0:3,u=xAxis?0:2;
            segments.add(new Segment(hits[a+u],hits[a+1],hits[b+u],hits[b+1]));
        }

        public List<PlanarGeometry.Rect> rectangles(double left,double bottom,double right,double top) {
            List<PlanarGeometry.Rect> result=new ArrayList<>();
            Map<Interval,Integer> previous=new HashMap<>();
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
                Map<Interval,Integer> current=new HashMap<>();
                for(var span:merged) {
                    // Reuse the immutable span, retaining exact endpoints without formatting a key per row.
                    Integer index=previous.get(span);
                    if(index==null) {index=result.size();result.add(new PlanarGeometry.Rect(span.start(),row/16.0,span.end(),(row+1)/16.0));}
                    else {var old=result.get(index);result.set(index,new PlanarGeometry.Rect(old.left(),old.bottom(),old.right(),(row+1)/16.0));}
                    current.put(span,index);
                }
                previous=current;
            }
            return result;
        }
    }
}
