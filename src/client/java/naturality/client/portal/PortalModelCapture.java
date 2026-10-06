package naturality.client.portal;

import java.lang.reflect.*;
import java.util.List;
import com.mojang.blaze3d.vertex.*;
import naturality.portal.PortalCrossing;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Runs the renderer's model submissions into a geometry-only consumer; nothing is drawn. */
public final class PortalModelCapture implements VertexConsumer {
    public static boolean capturing;
    private final @org.jspecify.annotations.Nullable PortalModelSection section;
    private final Vec3 origin;
    private final Vec3[] quad=new Vec3[4];
    private int vertexCount;
    private boolean modelSubmitted;
    private net.minecraft.resources.@org.jspecify.annotations.Nullable Identifier texture;
    public boolean flatCube(net.minecraft.client.model.geom.ModelPart.Cube cube,PoseStack.Pose pose) {
        return meshSink!=null && FlatModelAlpha.emit(cube,pose,texture,meshSink);
    }
    private java.util.function.@org.jspecify.annotations.Nullable Consumer<Vec3[]> meshSink;

    private PortalModelCapture(Vec3 origin, java.util.function.Consumer<Vec3[]> sink) {
        this.origin=origin;
        section=null;
        meshSink=sink;
    }

    private PortalModelCapture(PortalCrossing crossing) {
        origin=Vec3.atLowerCornerOf(crossing.anchor);
        section=new PortalModelSection(crossing.axis==Direction.Axis.X,crossing.plane);
    }
    /** Null means unsupported renderer. An empty list means the model does not intersect the plane. */
    public static @org.jspecify.annotations.Nullable List<PortalGlowOcclusion.Rect> capture(Entity entity,PortalCrossing crossing,Camera camera,float partialTick) {
        var capture=new PortalModelCapture(crossing);
        capture.render(entity,camera,partialTick);
        if(!capture.modelSubmitted) return null;
        boolean x=crossing.axis==Direction.Axis.X;
        var section = capture.section;
        if (section == null) return null;
        return section.rectangles(x?crossing.min.getX():crossing.min.getZ(),crossing.min.getY(),
            (x?crossing.max.getX():crossing.max.getZ())+1,crossing.max.getY()+1);
    }
    /** Capture once for effects which intersect several surface planes. Coordinates are origin-relative. */
    public static List<Vec3[]> mesh(Entity entity, Vec3 origin, Camera camera, float partialTick) {
        var quads=new java.util.ArrayList<Vec3[]>();
        var capture=new PortalModelCapture(origin,quads::add);
        capture.render(entity,camera,partialTick);
        return quads;
    }
    private void render(Entity entity, Camera camera, float partialTick) {
        var capture=this;
        var collector=(SubmitNodeCollector)Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),
            new Class<?>[]{SubmitNodeCollector.class},(proxy,method,args)-> {
                if(method.getName().equals("order")) return proxy;
                // Sprite-based submissions (e.g. shields) also have nine arguments.
                // Their default adapter resolves sprites into the canonical overload.
                if(method.isDefault()) return InvocationHandler.invokeDefault(proxy,method,args);
                if(method.getName().equals("submitModel") && args.length==9) {
                    capture.model((Model<?>)args[0],args[1],(PoseStack)args[2],args[3],(int)args[4],(int)args[5],(int)args[6]);
                    return null;
                }
                return null;
            });
        var dispatcher=Minecraft.getInstance().getEntityRenderDispatcher();
        var state=dispatcher.extractEntity(entity,partialTick);
        var cameraState=new CameraRenderState();cameraState.pos=camera.position();cameraState.orientation.set(camera.rotation());
        Vec3 position=entity.getPosition(partialTick).subtract(capture.origin);
        boolean previous=capturing;
        capturing=true;
        try {dispatcher.submit(state,cameraState,position.x,position.y,position.z,new PoseStack(),collector);}
        finally {capturing=previous;}
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private void model(Model model,Object state,PoseStack pose,Object renderType,int light,int overlay,int color) {
        modelSubmitted=true;vertexCount=0;
        texture=meshSink==null?null:FlatModelAlpha.texture(renderType);
        model.setupAnim(state);
        model.renderToBuffer(pose,this,light,overlay,color);
        vertexCount=0;
    }
    @Override public VertexConsumer addVertex(float x,float y,float z) {
        quad[vertexCount++]=meshSink==null ? new Vec3(x+origin.x,y+origin.y,z+origin.z) : new Vec3(x,y,z);
        if(vertexCount==4) {
            var section = this.section;
            var sink = meshSink;
            if(sink != null) sink.accept(quad.clone()); else if(section != null) section.quad(quad);
            vertexCount=0;
        }
        return this;
    }
    @Override public VertexConsumer setColor(int r,int g,int b,int a){return this;}
    @Override public VertexConsumer setColor(int color){return this;}
    @Override public VertexConsumer setUv(float u,float v){return this;}
    @Override public VertexConsumer setUv1(int u,int v){return this;}
    @Override public VertexConsumer setUv2(int u,int v){return this;}
    @Override public VertexConsumer setUv3(float u,float v){return this;}
    @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
    @Override public VertexConsumer setLineWidth(float width){return this;}
}

