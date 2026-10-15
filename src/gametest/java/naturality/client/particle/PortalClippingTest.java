package naturality.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import naturality.client.portal.PortalClippedVertexConsumer;

public final class PortalClippingTest {
    public static void run() {
        for(boolean xAxis:new boolean[]{false,true}) for(int side:new int[]{-1,1}) {
            var vertices=new ArrayList<float[]>();
            VertexConsumer sink=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class},(proxy,method,args)-> {
                    if(method.getName().equals("addVertex")) vertices.add(new float[]{(float)args[0],(float)args[1],(float)args[2],0,0});
                    if(method.getName().equals("setUv")) {vertices.getLast()[3]=(float)args[0];vertices.getLast()[4]=(float)args[1];}
                    return proxy;
                });
            var clip=new PortalClippedVertexConsumer(sink,xAxis,0,side);
            for(int i=0;i<4;i++) {
                float normal=i<2?-2:2, tangent=i==0 || i==3?-1:1;
                clip.addVertex(xAxis?tangent:normal,0,xAxis?normal:tangent).setUv((normal+2)/4,(tangent+1)/2);
            }
            clip.finish();
            require(vertices.size()==4,"Straddling large quad must remain a clipped quad");
            int boundary=0;
            for(var v:vertices) {
                float normal=v[xAxis?2:0];
                require(normal*side>=0,"Hidden geometry must never be emitted at any viewing angle");
                if(normal==0) {boundary++;require(Math.abs(v[3]-0.5)<0.00001,"Clipped texture coordinates must interpolate");}
            }
            require(boundary==2,"Cut must end exactly at the portal plane");
            vertices.clear();
            for(int i=0;i<4;i++) clip.addVertex(xAxis?i:-side,0,xAxis?-side:i);
            clip.finish();require(vertices.isEmpty(),"Fully hidden arrivals must emit no body geometry");
        }
    }
    private static void require(boolean condition,String message){if(!condition) throw new AssertionError(message);}
}
