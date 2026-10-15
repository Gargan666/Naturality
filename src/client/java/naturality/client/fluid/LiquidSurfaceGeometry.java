package naturality.client.fluid;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import java.util.function.DoubleBinaryOperator;

/** Rendered fluid heights shared by moving spray and the pixel columns of impact splashes. */
public final class LiquidSurfaceGeometry {
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
