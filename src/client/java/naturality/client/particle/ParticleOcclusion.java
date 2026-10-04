package naturality.client.particle;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Conservative perspective coverage by a continuous plane of opaque full blocks. */
public final class ParticleOcclusion {
    private ParticleOcclusion() { }

    public static boolean hidden(Vec3 eye,AABB bounds,Predicate<BlockPos> solid) {
        if(bounds.contains(eye))return false;
        double dx=(bounds.minX+bounds.maxX)*.5-eye.x;
        double dy=(bounds.minY+bounds.maxY)*.5-eye.y;
        double dz=(bounds.minZ+bounds.maxZ)*.5-eye.z;
        int x=(int)Math.floor(eye.x),y=(int)Math.floor(eye.y),z=(int)Math.floor(eye.z);
        int sx=dx>0?1:-1,sy=dy>0?1:-1,sz=dz>0?1:-1;
        double tx=next(eye.x,x,dx),ty=next(eye.y,y,dy),tz=next(eye.z,z,dz);
        double stepX=dx==0?Double.POSITIVE_INFINITY:Math.abs(1/dx);
        double stepY=dy==0?Double.POSITIVE_INFINITY:Math.abs(1/dy);
        double stepZ=dz==0?Double.POSITIVE_INFINITY:Math.abs(1/dz);
        var cell=new BlockPos.MutableBlockPos();
        // Unknown or distant paths fail open. Never traverse or load unbounded terrain.
        for(int step=0;step<160;step++) {
            int axis;
            double t;
            if(tx<=ty && tx<=tz){t=tx;x+=sx;tx+=stepX;axis=0;}
            else if(ty<=tz){t=ty;y+=sy;ty+=stepY;axis=1;}
            else {t=tz;z+=sz;tz+=stepZ;axis=2;}
            if(t>=1 || !Double.isFinite(t))return false;
            cell.set(x,y,z);
            if(solid.test(cell) && covered(eye,bounds,x,y,z,axis,solid))return true;
        }
        return false;
    }

    private static double next(double eye,int cell,double delta) {
        return delta==0?Double.POSITIVE_INFINITY:((delta>0?cell+1:cell)-eye)/delta;
    }

    private static boolean covered(Vec3 eye,AABB box,int x,int y,int z,int axis,Predicate<BlockPos> solid) {
        double origin=axis==0?eye.x:axis==1?eye.y:eye.z;
        int slab=axis==0?x:axis==1?y:z;
        if(origin>=slab && origin<=slab+1)return false;
        double plane=origin<slab?slab:slab+1;
        double minU=Double.POSITIVE_INFINITY,minV=minU,maxU=Double.NEGATIVE_INFINITY,maxV=maxU;
        for(int corner=0;corner<8;corner++) {
            double px=(corner&1)==0?box.minX:box.maxX;
            double py=(corner&2)==0?box.minY:box.maxY;
            double pz=(corner&4)==0?box.minZ:box.maxZ;
            double target=axis==0?px:axis==1?py:pz;
            double t=(plane-origin)/(target-origin);
            if(!(t>0 && t<1))return false;
            double u=axis==0?eye.y+(py-eye.y)*t:eye.x+(px-eye.x)*t;
            double v=axis==2?eye.y+(py-eye.y)*t:eye.z+(pz-eye.z)*t;
            minU=Math.min(minU,u);maxU=Math.max(maxU,u);
            minV=Math.min(minV,v);maxV=Math.max(maxV,v);
        }
        int u0=(int)Math.floor(minU-1e-6),u1=(int)Math.floor(maxU+1e-6);
        int v0=(int)Math.floor(minV-1e-6),v1=(int)Math.floor(maxV+1e-6);
        if((long)(u1-u0+1)*(v1-v0+1)>64)return false;
        var cell=new BlockPos.MutableBlockPos();
        for(int u=u0;u<=u1;u++)for(int v=v0;v<=v1;v++) {
            if(axis==0)cell.set(slab,u,v);
            else if(axis==1)cell.set(u,slab,v);
            else cell.set(u,v,slab);
            if(!solid.test(cell))return false;
        }
        return true;
    }
}
