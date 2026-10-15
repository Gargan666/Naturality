package naturality.client.portal;

import java.lang.reflect.*;
import net.minecraft.client.renderer.*;

/** Carry a plane from entity extraction into deferred feature submissions. */
public final class PortalClippedCollector {
    public record Plane(boolean xAxis,double coordinate,int side) {
        public PortalClippedVertexConsumer wrap(com.mojang.blaze3d.vertex.VertexConsumer buffer) {
            return new PortalClippedVertexConsumer(buffer,xAxis,coordinate,side);
        }
    }
    private static @org.jspecify.annotations.Nullable Plane current;
    public static void remember(Object submit) {
        if (submit instanceof PortalClippedSubmit clipped) clipped.naturality$clipPlane(current);
    }
    public static @org.jspecify.annotations.Nullable Plane plane(Object submit) {
        return submit instanceof PortalClippedSubmit clipped ? clipped.naturality$clipPlane() : null;
    }
    public static SubmitNodeCollector wrap(SubmitNodeCollector target,boolean xAxis,double plane,int side) {
        return (SubmitNodeCollector)proxy(target,SubmitNodeCollector.class,new Plane(xAxis,plane,side));
    }
    private static Object proxy(OrderedSubmitNodeCollector target,Class<?> type,Plane plane) {
        return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)-> {
            if(method.getName().equals("order")) return proxy(((SubmitNodeCollector)target).order((int)args[0]),OrderedSubmitNodeCollector.class,plane);
            if(method.isDefault()) return InvocationHandler.invokeDefault(proxy,method,args);
            if(method.getName().equals("submitCustomGeometry")) {
                var original=(SubmitNodeCollector.CustomGeometryRenderer)args[2];
                args[2]=(SubmitNodeCollector.CustomGeometryRenderer)(pose,buffer)-> {
                    var clipped=plane.wrap(buffer);original.render(pose,clipped);clipped.finish();
                };
            }
            Plane previous=current;current=plane;
            try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}
            finally {current=previous;}
        });
    }
    private PortalClippedCollector(){}
}
