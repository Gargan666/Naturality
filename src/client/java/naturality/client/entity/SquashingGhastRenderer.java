package naturality.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.GhastRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.GhastRenderState;
import net.minecraft.world.entity.monster.Ghast;

/** Scales the whole animated model around its body center, including tentacles. */
public final class SquashingGhastRenderer extends GhastRenderer {
    public static final class State extends GhastRenderState implements GhastTentacleState { public float squash, leanPitch, leanRoll; public GhastCrashPose crash; public GhastTentaclePhysics.Pose tentacles; public GhastTentaclePhysics.Pose naturality$tentacles() { return tentacles; } }
    public SquashingGhastRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(Ghast entity, GhastRenderState state, float partial) {
        super.extractRenderState(entity, state, partial);
        ((State)state).crash = GhastCrashPose.extract(entity, partial);
        ((State)state).tentacles = GhastTentaclePhysics.extract(entity,partial,((State)state).crash);
        if (((State)state).crash != null) { state.deathTime = 0; state.hasRedOverlay = entity.hurtTime > 0 || ((naturality.ghast.GhastCrashBody)entity).naturality$impactTicks() >= 0; }
        ((State)state).squash = GhastSquash.sample(entity, partial);
        GhastSquash.extractLean(entity, (State)state, partial);
    }
    @Override protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(Ghast entity,float partial) {
        var bounds=super.getBoundingBoxForCulling(entity,partial);
        return entity.isDeadOrDying() ? bounds.inflate(entity.getBbWidth()*2) : bounds;
    }
    @Override protected void scale(GhastRenderState state, PoseStack pose) {
        super.scale(state, pose);
        float vertical = 1 - ((State)state).squash;
        float horizontal = (float)(1 / Math.sqrt(vertical));
        float pivot = -1.8045F;
        pose.translate(0, pivot, 0);
        if (((State)state).crash != null) ((State)state).crash.apply(pose,state.bodyRot);
        else {
            pose.scale(horizontal, vertical, horizontal);
        }
        pose.translate(0, -pivot, 0);
    }
}

