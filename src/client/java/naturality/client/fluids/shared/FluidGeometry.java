package naturality.client.fluids.shared;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import naturality.client.fluids.lava.LavaEffects.LavaSurface;
import naturality.client.fluids.water.WaterShaders.WaterSurface;
import net.minecraft.client.multiplayer.ClientLevel;
import java.util.function.DoubleBinaryOperator;
import naturality.client.api.geometry.PlanarGeometry;
import naturality.client.api.geometry.PlanarGeometry.ModelSection;
import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Join exposed liquid faces while retaining the inset against block geometry. */
@org.jspecify.annotations.NullMarked
public final class FluidGeometry {
    private FluidGeometry() {}

    public static float connectedHeight(BlockAndTintGetter level, BlockPos pos, float height) {
        int local = pos.getY() & 15;
        float relative = height - local;
        if (Math.abs(relative - 0.001F) < 0.0001F && level.getBlockState(pos.below()).isAir())
            return local;
        // A full-height corner can be lowered by vanilla's top-face epsilon,
        // leaving a crack against a neighboring column at the integer boundary.
        if (Math.abs(relative - 0.999F) < 0.0001F && level.getBlockState(pos.above()).isAir())
            return local + 1;
        return height;
    }

    public static float connectedCoordinate(BlockAndTintGetter level, BlockPos pos, float coordinate, boolean xAxis) {
        int local = (xAxis ? pos.getX() : pos.getZ()) & 15;
        float relative = coordinate - local;
        Direction side;
        float boundary;
        if (Math.abs(relative - 0.001F) < 0.0001F) {
            side = xAxis ? Direction.WEST : Direction.NORTH;
            boundary = local;
        } else if (Math.abs(relative - 0.999F) < 0.0001F) {
            side = xAxis ? Direction.EAST : Direction.SOUTH;
            boundary = local + 1;
        } else return coordinate;
        return level.getBlockState(pos.relative(side)).isAir() ? boundary : coordinate;
    }

    /** Rendered fluid heights shared by moving spray and the pixel columns of impact splashes. */
    public static final class LiquidSurfaceGeometry {
        public record Sample(BlockPos pos,double x,double z,double height,double downhillX,double downhillZ) {}
        private LiquidSurfaceGeometry() {}
        public static @org.jspecify.annotations.Nullable Sample at(ClientLevel level,BlockPos pos,double x,double z,boolean lava) {
            double u=Math.clamp(x-pos.getX(),.01,.99),v=Math.clamp(z-pos.getZ(),.01,.99);
            if(lava) {
                var surface=LavaSurface.at(level,pos);
                if(surface==null || !surface.contains(u,v))return null;
                return sample(pos,u,v,surface::height);
            }
            var surface=WaterSurface.at(level,pos);
            if(surface==null || !surface.contains(u,v))return null;
            return sample(pos,u,v,surface::height);
        }
        private static Sample sample(BlockPos pos,double u,double v,DoubleBinaryOperator height) {
            double gx=height.applyAsDouble(Math.min(.99,u+.04),v)-height.applyAsDouble(Math.max(.01,u-.04),v);
            double gz=height.applyAsDouble(u,Math.min(.99,v+.04))-height.applyAsDouble(u,Math.max(.01,v-.04));
            return new Sample(pos,pos.getX()+u,pos.getZ()+v,height.applyAsDouble(u,v),-gx,-gz);
        }
        public static @org.jspecify.annotations.Nullable Sample near(ClientLevel level,double x,double y,double z,boolean lava,boolean edge) {
            var origin=BlockPos.containing(x,y,z);
            Sample best=null;
            double score=Double.POSITIVE_INFINITY;
            int radius=edge?1:0;
            for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++) {
                int bx=origin.getX()+dx,bz=origin.getZ()+dz;
                if(x<bx-.08 || x>bx+1.08 || z<bz-.08 || z>bz+1.08)continue;
                for(int dy=1;dy>=-3;dy--) {
                    var candidate=at(level,new BlockPos(bx,origin.getY()+dy,bz),x,z,lava);
                    if(candidate==null)continue;
                    double distance=Math.abs(candidate.height-y)+4*Math.hypot(candidate.x-x,candidate.z-z);
                    if(distance<score){score=distance;best=candidate;}
                }
            }
            return best;
        }
    }

    /** Geometry-only water cross sections and a non-overlapping, three-pixel square-distance rim. */
    public static final class WaterIntersectionGeometry {
        private WaterIntersectionGeometry() {}
        public static List<PlanarGeometry.Rect> section(List<Vec3[]> mesh, double height, double dx, double dz,
                                                             double left, double bottom, double right, double top) {
            var section = new ModelSection(true, height);
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
        public static float alpha(List<PlanarGeometry.Rect> sections, double x, double z) {
            double distance=Double.POSITIVE_INFINITY;
            for (var rect:sections) distance=Math.min(distance,PlanarGeometry.distance(rect,x,z));
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

    /** Terrain tint-alpha tags: 254 fully wet; 246..253 encode partial water heights. */
    public static final class SubmergedFace {
        private SubmergedFace() {}

        public static int tag(float minY, float maxY, int fluidY, float height) {
            float surface = fluidY + height;
            if (minY > surface + 0.0001F || maxY < fluidY) return 255;
            if (maxY <= surface + 0.0001F) return 254;
            // Partial vanilla block faces stay within the fluid's block. The shader
            // tests their fractional Y against the exact 1/9-block fluid height.
            // Do not tag oversized/custom faces whose block origin is ambiguous.
            if (minY < fluidY || maxY > fluidY + 1.0001F) return 255;
            return 245 + Math.clamp(Math.round(height * 9.0F), 1, 8);
        }
    }
}
