package naturality.client.portal;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;

/** Clips textured quads in camera-relative space, interpolating every vertex attribute. */
public final class PortalClippedVertexConsumer implements VertexConsumer {
    private final VertexConsumer output;
    private final boolean xAxis;
    private final double plane;
    private final int side;
    private final List<float[]> quad=new ArrayList<>(4);
    private float[] vertex;
    public PortalClippedVertexConsumer(VertexConsumer output,boolean xAxis,double plane,int side) {
        this.output=output;this.xAxis=xAxis;this.plane=plane;this.side=side;
    }
    private double distance(float[] v) {return (v[xAxis?2:0]-plane)*side;}
    private float[] interpolate(float[] a,float[] b,double t) {
        float[] v=new float[a.length];
        for(int i=0;i<v.length;i++) v[i]=(float)(a[i]+(b[i]-a[i])*t);
        return v;
    }
    private void flush() {
        if(quad.isEmpty()) return;
        List<float[]> polygon=new ArrayList<>(6);
        float[] a=quad.getLast();double da=distance(a);
        for(float[] b:quad) {
            double db=distance(b);
            if((da>=0)!=(db>=0)) polygon.add(interpolate(a,b,da/(da-db)));
            if(db>=0) polygon.add(b);
            a=b;da=db;
        }
        if(polygon.size()==4) for(float[] v:polygon) emit(v);
        else for(int i=1;i+1<polygon.size();i++) {
            emit(polygon.getFirst());emit(polygon.get(i));emit(polygon.get(i+1));emit(polygon.get(i+1));
        }
        quad.clear();
    }
    private void emit(float[] v) {
        output.addVertex(v[0],v[1],v[2]).setColor(Math.round(v[3]),Math.round(v[4]),Math.round(v[5]),Math.round(v[6]))
            .setUv(v[7],v[8]).setUv1(Math.round(v[9]),Math.round(v[10])).setUv2(Math.round(v[11]),Math.round(v[12]))
            .setNormal(v[13],v[14],v[15]).setUv3(v[16],v[17]).setLineWidth(v[18]);
    }
    /** The final vertex is complete only after its attributes have been written. */
    public void finish(){flush();}
    @Override public VertexConsumer addVertex(float x,float y,float z) {
        if(quad.size()==4) flush();
        vertex=new float[]{x,y,z,255,255,255,255,0,0,0,0,0,0,0,0,0,0,0,1};
        quad.add(vertex);return this;
    }
    @Override public VertexConsumer setColor(int r,int g,int b,int a){vertex[3]=r;vertex[4]=g;vertex[5]=b;vertex[6]=a;return this;}
    @Override public VertexConsumer setColor(int c){return setColor(c>>16&255,c>>8&255,c&255,c>>>24);}
    @Override public VertexConsumer setUv(float u,float v){vertex[7]=u;vertex[8]=v;return this;}
    @Override public VertexConsumer setUv1(int u,int v){vertex[9]=u;vertex[10]=v;return this;}
    @Override public VertexConsumer setUv2(int u,int v){vertex[11]=u;vertex[12]=v;return this;}
    @Override public VertexConsumer setNormal(float x,float y,float z){vertex[13]=x;vertex[14]=y;vertex[15]=z;return this;}
    @Override public VertexConsumer setUv3(float u,float v){vertex[16]=u;vertex[17]=v;return this;}
    @Override public VertexConsumer setLineWidth(float width){vertex[18]=width;return this;}
}
