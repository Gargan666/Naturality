package naturality.client.portal;

import java.util.*;
import java.lang.reflect.Field;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Alpha-cut geometry only for zero-thickness model cubes. Cache stores compact CPU masks. */
public final class FlatModelAlpha {
    private record Mask(int width,int height,BitSet opaque) {
        boolean at(int x,int y){return opaque.get(Math.clamp(y,0,height-1)*width+Math.clamp(x,0,width-1));}
    }
    private static final Map<Identifier,Optional<Mask>> MASKS=new HashMap<>();
    public static int lastCutPixels;
    public static void clear(){MASKS.clear();}
    private static Object field(Object owner,String name) throws ReflectiveOperationException {
        Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    public static @org.jspecify.annotations.Nullable Identifier texture(Object renderType) {
        try {
            Object state=field(renderType,"state");
            var bindings=(Map<?,?>)field(state,"textures");
            Object binding=bindings.get("Sampler0");
            return binding==null?null:(Identifier)field(binding,"location");
        } catch(ReflectiveOperationException|RuntimeException ignored){return null;}
    }
    private static Optional<Mask> load(Identifier texture) {
        try(var stream=Minecraft.getInstance().getResourceManager().open(texture);var image=NativeImage.read(stream)) {
            if(image.getWidth()>4096 || image.getHeight()>4096)return Optional.empty();
            var bits=new BitSet(image.getWidth()*image.getHeight());
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)
                if((image.getPixel(x,y)>>>24)>=26)bits.set(y*image.getWidth()+x);
            return Optional.of(new Mask(image.getWidth(),image.getHeight(),bits));
        } catch(Exception ignored){return Optional.empty();}
    }
    public static boolean emit(ModelPart.Cube cube,PoseStack.Pose pose,@org.jspecify.annotations.Nullable Identifier texture,java.util.function.Consumer<Vec3[]> sink) {
        if(texture==null || (cube.maxX!=cube.minX && cube.maxY!=cube.minY && cube.maxZ!=cube.minZ))return false;
        if(MASKS.size()>256)MASKS.clear();
        var optional=MASKS.computeIfAbsent(texture,FlatModelAlpha::load);
        if(optional.isEmpty())return false;
        var mask=optional.get();
        var clipped=new ArrayList<Vec3[]>();
        for(var polygon:cube.polygons) {
            var v=polygon.vertices();
            Vec3 p=position(v[0],pose),u=position(v[1],pose).subtract(p),w=position(v[3],pose).subtract(p);
            if(u.cross(w).lengthSqr()<1e-14)continue;
            double du=v[1].u()-v[0].u(),dv=v[3].v()-v[0].v();
            // Vanilla model cubes use rectangular UVs; unknown UV layouts keep their original geometry.
            if(Math.abs(du)<1e-10 || Math.abs(dv)<1e-10)return false;
            int x0=(int)Math.floor(Math.min(v[0].u(),v[1].u())*mask.width+1e-4);
            int x1=(int)Math.ceil(Math.max(v[0].u(),v[1].u())*mask.width-1e-4);
            int y0=(int)Math.floor(Math.min(v[0].v(),v[3].v())*mask.height+1e-4);
            int y1=(int)Math.ceil(Math.max(v[0].v(),v[3].v())*mask.height-1e-4);
            if((long)(x1-x0)*(y1-y0)>16384)return false;
            for(int y=y0;y<y1;y++)for(int x=x0;x<x1;) {
                if(!mask.at(x,y)){x++;continue;}
                int start=x++;while(x<x1 && mask.at(x,y))x++;
                lastCutPixels+=x-start;
                double a=(start/(double)mask.width-v[0].u())/du,b=(x/(double)mask.width-v[0].u())/du;
                double c=(y/(double)mask.height-v[0].v())/dv,d=((y+1)/(double)mask.height-v[0].v())/dv;
                double loU=Math.clamp(Math.min(a,b),0,1),hiU=Math.clamp(Math.max(a,b),0,1);
                double loV=Math.clamp(Math.min(c,d),0,1),hiV=Math.clamp(Math.max(c,d),0,1);
                prism(new Vec3[]{p.add(u.scale(loU)).add(w.scale(loV)),p.add(u.scale(hiU)).add(w.scale(loV)),
                    p.add(u.scale(hiU)).add(w.scale(hiV)),p.add(u.scale(loU)).add(w.scale(hiV))},clipped::add);
            }
        }
        clipped.forEach(sink);
        return true;
    }
    private static Vec3 position(ModelPart.Vertex v,PoseStack.Pose pose) {
        var p=pose.pose().transformPosition(v.worldX(),v.worldY(),v.worldZ(),new Vector3f());
        return new Vec3(p.x,p.y,p.z);
    }
    private static void prism(Vec3[] q,java.util.function.Consumer<Vec3[]> sink) {
        Vec3 n=q[1].subtract(q[0]).cross(q[3].subtract(q[0])).normalize().scale(1.0/2048);
        Vec3[] a=new Vec3[4],b=new Vec3[4];
        for(int i=0;i<4;i++){a[i]=q[i].add(n);b[i]=q[i].subtract(n);}
        sink.accept(a);sink.accept(new Vec3[]{b[3],b[2],b[1],b[0]});
        for(int i=0;i<4;i++){int j=(i+1)%4;sink.accept(new Vec3[]{a[j],a[i],b[i],b[j]});}
    }
}

