package naturality.client.systems.gravity.mixins;

import naturality.systems.gravity.EndGravity;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.client.systems.gravity.GravityPreviewContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import naturality.client.systems.gravity.GravityPreviewContext.EndGravityRenderState;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Shadow;
import naturality.systems.weather.end.framework.EndWeatherSystem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.world.entity.ElytraAnimationState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class GravityMixins {
    private GravityMixins() {}

    @Mixin(LocalPlayer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class DistortionCreativeFlightMixin {
        @ModifyArg(method="aiStep",at=@At(value="INVOKE",target="Lnet/minecraft/world/phys/Vec3;add(DDD)Lnet/minecraft/world/phys/Vec3;"),index=1)
        private double naturality$gravityLocalFlight(double y) {
            return EndGravity.inverted((LocalPlayer)(Object)this)?-y:y;
        }
    }

    @Mixin(InventoryScreen.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class DistortionInventoryPreviewMixin {
        @WrapMethod(method="extractRenderState(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;")
        private static EntityRenderState naturality$inventoryOrientation(LivingEntity entity,Operation<EntityRenderState> original) {
            boolean previous=GravityPreviewContext.enter();
            try{return original.call(entity);}finally{GravityPreviewContext.restore(previous);}
        }
    }

    @Mixin(AvatarRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndGravityAvatarMixin {
        @Inject(method="getRenderOffset",at=@At("RETURN"),cancellable=true)
        private void naturality$poseOffset(AvatarRenderState state,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.phys.Vec3> cir) {
            float inversion=((EndGravityRenderState)state).naturality$inversion();
            if(inversion==0)return;
            var offset=cir.getReturnValue();
            cir.setReturnValue(new net.minecraft.world.phys.Vec3(offset.x,offset.y*Math.cos(Math.PI*inversion),offset.z));
        }
        @Inject(method="extractFlightData",at=@At("TAIL"))
        private void naturality$bank(Avatar entity, AvatarRenderState state, float partial, CallbackInfo ci) {
            state.flyingYRot*=(float)Math.cos(Math.PI*((EndGravityRenderState)state).naturality$inversion());
        }
    }

    @Mixin(Camera.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndGravityCameraMixin {
        @Shadow private @org.jspecify.annotations.Nullable Entity entity;
        @ModifyArg(method="setRotation",at=@At(value="INVOKE",target="Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;"),index=2)
        private float naturality$roll(float roll) {
            var cameraEntity = entity;
            return cameraEntity==null?roll:roll+(float)Math.PI*EndGravity.inversion(cameraEntity,true);
        }
        @ModifyExpressionValue(method="alignWithEntity",at=@At(value="INVOKE",target="Lnet/minecraft/util/Mth;lerp(FFF)F"))
        private float naturality$eye(float smoothedHeight) {
            // Transform the interpolated eye height itself. Using the new pose's
            // raw height here makes elytra/crouch changes jump through the ceiling.
            var cameraEntity = entity;
            return cameraEntity==null?smoothedHeight:EndGravity.eyeHeight(cameraEntity,smoothedHeight,true);
        }
    }

    @Mixin(Particle.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndGravityParticleMixin {
        @SuppressWarnings("null") @Shadow protected ClientLevel level;
        @ModifyExpressionValue(method="tick",at=@At(value="FIELD",target="Lnet/minecraft/client/particle/Particle;gravity:F"))
        private float naturality$gravity(float gravity) { return gravity*EndWeatherSystem.state(level).strength(); }
    }

    @Mixin(LivingEntityRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndGravityRendererMixin {
        @Inject(method="extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",at=@At("TAIL"))
        private void naturality$extract(LivingEntity e,LivingEntityRenderState state,float partial,CallbackInfo ci) {
            float inversion=naturality.client.systems.gravity.GravityPreviewContext.active()?0:EndGravity.inversion(e,true);
            ((EndGravityRenderState)state).naturality$inversion(inversion);
            if(inversion==0)return;
            // World look direction expressed in the rolled body's local frame.
            // At a full flip both pitch and relative head yaw reverse; projecting
            // the direction also keeps the head tracking correctly during the turn.
            double yaw=Math.toRadians(state.yRot),pitch=Math.toRadians(state.xRot);
            double angle=Math.PI*inversion,cos=Math.cos(angle),sin=Math.sin(angle);
            double x=Math.sin(yaw)*Math.cos(pitch),y=-Math.sin(pitch),z=-Math.cos(yaw)*Math.cos(pitch);
            double localX=cos*x+sin*y,localY=-sin*x+cos*y;
            state.yRot=(float)Math.toDegrees(Math.atan2(localX,-z));
            state.xRot=(float)Math.toDegrees(Math.atan2(-localY,Math.hypot(localX,z)));
        }
        @Inject(method="setupRotations",at=@At("TAIL"))
        private void naturality$rotate(LivingEntityRenderState state,PoseStack pose,float body,float scale,CallbackInfo ci) {
            float inversion=((EndGravityRenderState)state).naturality$inversion();if(inversion==0)return;
            float half=state.boundingBoxHeight/scale/2;
            pose.translate(0,half,0);pose.rotateDegrees(Axis.ZP,inversion*180);pose.translate(0,-half,0);
        }
    }

    @Mixin(LivingEntityRenderState.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndGravityRenderStateMixin implements EndGravityRenderState {
        @Unique private float naturality$inversion;
        public float naturality$inversion() { return naturality$inversion; }
        public void naturality$inversion(float value) { naturality$inversion=value; }
    }

    @Mixin(ElytraAnimationState.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndGravityWingsMixin {
        @SuppressWarnings("null") @Shadow @Final private LivingEntity entity;
        @ModifyExpressionValue(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;"))
        private Vec3 naturality$localWingMotion(Vec3 movement) {
            return EndGravity.inverted(entity)?new Vec3(movement.x,-movement.y,movement.z):movement;
        }
    }
}
