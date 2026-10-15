package naturality.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.HappyGhastRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.HappyGhastRenderState;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;

/** Whole-model movement lean, including the vanilla harness and ropes layers. */
public final class LeaningHappyGhastRenderer extends HappyGhastRenderer {
    public static final class State extends HappyGhastRenderState implements GhastTentacleState { public float leanPitch, leanRoll; public GhastCrashPose crash; public GhastTentaclePhysics.Pose tentacles; public GhastTentaclePhysics.Pose naturality$tentacles() { return tentacles; } }
    public LeaningHappyGhastRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(HappyGhast entity, HappyGhastRenderState state, float partial) {
        super.extractRenderState(entity, state, partial);
        ((State)state).crash = GhastCrashPose.extract(entity,partial);
        ((State)state).tentacles = GhastTentaclePhysics.extract(entity,partial,((State)state).crash);
        if (((State)state).crash != null) { state.deathTime = 0; state.hasRedOverlay = entity.hurtTime > 0 || ((naturality.ghast.GhastCrashBody)entity).naturality$impactTicks() >= 0; }
        var lean = GhastSquash.lean(entity, state.bodyRot, partial);
        ((State)state).leanPitch = lean.x;
        ((State)state).leanRoll = lean.y;
    }
    @Override protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(HappyGhast entity,float partial) {
        var bounds=super.getBoundingBoxForCulling(entity,partial);
        return entity.isDeadOrDying() ? bounds.inflate(entity.getBbWidth()*2) : bounds;
    }
    @Override protected void scale(HappyGhastRenderState state, PoseStack pose) {
        super.scale(state, pose);
        // Native mesh is scaled 4x about its feet; babies add the .2375 mesh transform.
        float pivot = -2.004F * (state.isBaby ? .2375F : 1);
        pose.translate(0, pivot, 0);
        if (((State)state).crash != null) ((State)state).crash.apply(pose,state.bodyRot);
        pose.translate(0, -pivot, 0);
    }
}

