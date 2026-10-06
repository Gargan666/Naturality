package naturality.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import naturality.Naturality;
import naturality.starfall.FallingStar;

public final class FallingStarRenderer extends EntityRenderer<FallingStar,FallingStarRenderer.State> {
    private static final ModelPart MODEL=model();
    public static final class State extends EntityRenderState { public float yaw,pitch,opacity; }
    public FallingStarRenderer(EntityRendererProvider.Context context) {super(context);}
    private static ModelPart model() {
        var mesh=new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("star",CubeListBuilder.create()
            .texOffs(0,20).addBox(-4,-4,-4,8,8,8)
            .texOffs(0,0).addBox(-5,-5,-5,10,10,10),PartPose.ZERO);
        return LayerDefinition.create(mesh,64,64).bakeRoot();
    }
    @Override public State createRenderState() {return new State();}
    @Override public void extractRenderState(FallingStar star,State state,float partial) {
        super.extractRenderState(star,state,partial);
        var velocity=star.getDeltaMovement();
        state.yaw=(float)Math.toDegrees(Math.atan2(velocity.x,velocity.z));
        state.pitch=(float)-Math.toDegrees(Math.atan2(velocity.y,velocity.horizontalDistance()));
        state.displayFireAnimation=false;state.opacity=star.opacity(partial);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        if(!state.isInvisible && state.opacity>0) {
            pose.pushPose();pose.translate(0,state.boundingBoxHeight/2,0);
            pose.rotate(Axis.YP.rotationDegrees(-state.yaw));pose.rotate(Axis.XP.rotationDegrees(state.pitch));
            pose.rotate(Axis.YP.rotationDegrees(-state.ageInTicks*6));pose.rotate(Axis.XP.rotationDegrees(state.ageInTicks*9));
            pose.scale(-2,-2,2);
            collector.submitCustomGeometry(pose,RenderTypes.entityTranslucent(Naturality.id("textures/entity/star.png")),(submitted,buffer)->{
                var modelPose=new PoseStack();modelPose.last().pose().set(submitted.pose());modelPose.last().normal().set(submitted.normal());
                MODEL.render(modelPose,buffer,15728880,OverlayTexture.NO_OVERLAY,net.minecraft.util.ARGB.colorFromFloat(state.opacity,1,1,1));
            });
            pose.popPose();
        }
        super.submit(state,pose,collector,camera);
    }
}