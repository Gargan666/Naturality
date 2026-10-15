package naturality.client.api.geometry;

import org.joml.Matrix3f;
import org.joml.Vector3f;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Velocity-aligned particle bases; preserve the distinct X- and Y-aligned card conventions. */
@org.jspecify.annotations.NullMarked
public final class ParticleGeometry {
    private ParticleGeometry() { }
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
    public static Quaternionf orientation(Vec3 velocity, Vec3 toCamera) {
        Vec3 direction = velocity.lengthSqr() < 1.0E-12 ? new Vec3(1, 0, 0) : velocity.normalize();
        Vector3f u = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
        Vector3f normal = new Vector3f((float) toCamera.x, (float) toCamera.y, (float) toCamera.z);
        normal.sub(new Vector3f(u).mul(normal.dot(u)));
        if (normal.lengthSquared() < 1.0E-8F) {
            // Exactly head-on: retain a stable plane perpendicular to the viewing ray.
            normal.set(Math.abs(u.y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(0, 0, 1));
            normal.sub(new Vector3f(u).mul(normal.dot(u)));
        }
        normal.normalize();
        Vector3f v = new Vector3f(normal).cross(u).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f(u, v, normal));
    }
}
