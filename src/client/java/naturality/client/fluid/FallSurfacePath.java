package naturality.client.fluid;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Spray falls freely until it reaches the stream, then follows its slopes and step edges. */
public final class FallSurfacePath {
    private final boolean lava;
    private boolean following;
    private double dx,dz;
    public FallSurfacePath(boolean lava){this.lava=lava;}
    public @org.jspecify.annotations.Nullable Vec3 step(ClientLevel level,double x,double y,double z,double vx,double vy,double vz) {
        double speed=lava?.045:.18;
        var current=LiquidSurfaceGeometry.near(level,x,y,z,lava,true);
        if(!following) {
            var landing=LiquidSurfaceGeometry.near(level,x+vx,y+vy,z+vz,lava,true);
            if(landing==null || vy>0 || y<landing.height()-.3 || y+vy>landing.height()+.035)return null;
            following=true;
            current=landing;
            double length=Math.hypot(vx,vz);
            if(length>.0001){dx=vx/length;dz=vz/length;}
        }
        if(current!=null && Math.abs(current.height()-y)<1.5) {
            var flow=level.getFluidState(current.pos()).getFlow(level,current.pos());
            double flowLength=Math.hypot(flow.x,flow.z);
            double length=Math.hypot(current.downhillX(),current.downhillZ());
            if(flowLength>.0001){dx=flow.x/flowLength;dz=flow.z/flowLength;}
            else if(length>.0001){dx=current.downhillX()/length;dz=current.downhillZ()/length;}
            x=current.x();z=current.z();
        }
        double nx=x+dx*speed,nz=z+dz*speed;
        // At a ledge the next tile must win; choosing the previous tile's
        // clamped edge repeatedly can trap slower lava spray at the boundary.
        var next=LiquidSurfaceGeometry.near(level,nx,y,nz,lava,false);
        if(next!=null && next.height()<=y+.25 && next.height()>=y-1.25)
            return new Vec3(next.x(),Math.max(next.height()+.035,y-speed*1.5),next.z());
        if(level.getBlockState(BlockPos.containing(nx,y-speed,nz)).isSolidRender()) {following=false;return null;}
        return new Vec3(nx,y-speed,nz);
    }
}
