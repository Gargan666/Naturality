package naturality.client.particle;

import org.joml.Matrix3f;
import org.joml.Quaternionf;

/** Velocity-locked Y axis with caller-owned scratch storage; no per-frame objects. */
public final class WeatherCardGeometry {
    private WeatherCardGeometry() { }
    public static Quaternionf orientation(double dx,double dy,double dz,double cx,double cy,double cz,
            Matrix3f basis,Quaternionf result) {
        double length=dx*dx+dy*dy+dz*dz;
        if(length<1E-12) {dx=1;dy=dz=0;}
        else {double inverse=1/Math.sqrt(length);dx*=inverse;dy*=inverse;dz*=inverse;}
        float ux=(float)dx,uy=(float)dy,uz=(float)dz;
        float nx=(float)cx,ny=(float)cy,nz=(float)cz;
        float dot=nx*ux+ny*uy+nz*uz;
        nx-=ux*dot;ny-=uy*dot;nz-=uz*dot;
        if(nx*nx+ny*ny+nz*nz<1E-8F) {
            nx=0;ny=Math.abs(uy)<.9F?1:0;nz=ny==1?0:1;
            dot=ny*uy+nz*uz;
            nx-=ux*dot;ny-=uy*dot;nz-=uz*dot;
        }
        float inverse=(float)(1/Math.sqrt(nx*nx+ny*ny+nz*nz));
        nx*=inverse;ny*=inverse;nz*=inverse;
        float vx=ny*uz-nz*uy,vy=nz*ux-nx*uz,vz=nx*uy-ny*ux;
        inverse=(float)(1/Math.sqrt(vx*vx+vy*vy+vz*vz));
        // This is the old X-aligned basis followed by its -90 degree Z turn.
        basis.set(-vx*inverse,-vy*inverse,-vz*inverse,ux,uy,uz,nx,ny,nz);
        return result.setFromNormalized(basis);
    }
}
