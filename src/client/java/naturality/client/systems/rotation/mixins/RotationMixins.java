package naturality.client.systems.rotation.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.systems.rotation.PlayerRotationRendering;
import naturality.systems.rotation.PlayerRotation;
import naturality.systems.rotation.PlayerRotation.Angles;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Camera, model and client movement hooks share the common server-owned body orientation. */
public final class RotationMixins {
    private RotationMixins() { }

    @Mixin(AvatarRenderState.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PlayerRotationRenderStateMixin implements PlayerRotationRendering.State {
        @Unique private Angles naturality$bodyRotation = Angles.ZERO;
        @Unique private float naturality$rotationPivot;
        @Unique private float naturality$rotationYaw;
        public float naturality$rotationYaw() { return naturality$rotationYaw; }
        public void naturality$rotationYaw(float yaw) { naturality$rotationYaw = yaw; }
        public Angles naturality$bodyRotation() { return naturality$bodyRotation; }
        public void naturality$bodyRotation(Angles angles) { naturality$bodyRotation = angles; }
        public float naturality$rotationPivot() { return naturality$rotationPivot; }
        public void naturality$rotationPivot(float height) { naturality$rotationPivot = height; }
    }

    @Mixin(AvatarRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PlayerRotationRendererMixin {
        @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
        private void naturality$extract(Avatar entity, AvatarRenderState state, float partial, CallbackInfo ci) {
            if (entity instanceof Player player) PlayerRotationRendering.extract(player, state, partial);
            else ((PlayerRotationRendering.State)state).naturality$bodyRotation(Angles.ZERO);
        }
        @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("HEAD"), cancellable = true)
        private void naturality$rotate(AvatarRenderState state, PoseStack pose, float bodyYaw, float scale, CallbackInfo ci) {
            if (!((PlayerRotationRendering.State)state).naturality$bodyRotation().active()) return;
            PlayerRotationRendering.rotate(state, pose, bodyYaw, scale);
            ci.cancel();
        }
        @Inject(method = "getRenderOffset", at = @At("RETURN"), cancellable = true)
        private void naturality$offset(AvatarRenderState state, CallbackInfoReturnable<Vec3> cir) {
            var angles = PlayerRotationRendering.relativeRotation(((PlayerRotationRendering.State)state).naturality$bodyRotation(),
                ((PlayerRotationRendering.State)state).naturality$rotationYaw(), state.bodyRot);
            if (angles.active()) cir.setReturnValue(angles.transform(cir.getReturnValue()));
        }
    }

    @Mixin(Camera.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PlayerRotationCameraMixin {
        @Shadow private @Nullable Entity entity;
        @Shadow protected abstract void setPosition(Vec3 position);
        @Shadow public abstract Vec3 position();
        @Unique private PlayerRotationRendering.CameraHandoff naturality$cameraHandoff = new PlayerRotationRendering.CameraHandoff();
        @Unique private @Nullable Entity naturality$previousCameraEntity;
        // Set the eye origin before vanilla's third-person obstruction sweep, not after that sweep.
        @Inject(method = "alignWithEntity", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Camera;detached:Z", opcode = Opcodes.PUTFIELD))
        private void naturality$eyePosition(float partial, CallbackInfo ci) {
            var cameraEntity = entity;
            if (cameraEntity != naturality$previousCameraEntity) {
                naturality$cameraHandoff = new PlayerRotationRendering.CameraHandoff();
                naturality$previousCameraEntity = cameraEntity;
            }
            if (cameraEntity instanceof Player player)
                setPosition(PlayerRotationRendering.cameraHandoff(player, partial, position(), naturality$cameraHandoff));
        }
    }

    @Mixin(Entity.class)
    public static abstract class PlayerRotationLightingMixin {
        @Inject(method = "getLightProbePosition", at = @At("HEAD"), cancellable = true)
        private void naturality$lightProbe(float partial, CallbackInfoReturnable<Vec3> cir) {
            if ((Object)this instanceof Player player && (PlayerRotation.terrainActive(player)
                    || PlayerRotation.interpolatedAngles(player, partial).active()))
                cir.setReturnValue(PlayerRotationRendering.cameraPosition(player, partial));
        }
    }

    @Mixin(net.minecraft.client.renderer.extract.LevelExtractor.class)
    public static abstract class PlayerRotationOverlayMixin {
        @Inject(method = "getViewBlockingState", at = @At("HEAD"), cancellable = true)
        private static void naturality$cameraObstruction(LocalPlayer player,
                net.minecraft.client.renderer.culling.Frustum frustum,
                CallbackInfoReturnable<net.minecraft.world.level.block.state.BlockState> cir) {
            if (!PlayerRotation.terrainActive(player) && !PlayerRotation.interpolatedAngles(player, 1).active()) return;
            var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera();
            cir.setReturnValue(PlayerRotationRendering.viewBlockingState(player, frustum.getNearPlaneBounds().move(camera.position())));
        }
    }

    @Mixin(LocalPlayer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PlayerRotationLocalPlayerMixin {
        @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
        private void naturality$avoidEnvelopePush(double x, double z, CallbackInfo ci) {
            var player = (LocalPlayer)(Object)this;
            // Envelope corners are not body contacts, even during a transient real overlap.
            // The oriented collision solver already permits movement out of penetration.
            if (PlayerRotation.terrainActive(player)) ci.cancel();
        }
    }

    /** Update vanilla's local ambience flag before LocalPlayer chooses entry/exit sounds. */
    @Mixin(Player.class)
    public static abstract class PlayerRotationListenerMixin {
        @Shadow protected boolean wasUnderwater;
        @Inject(method = "updateIsUnderwater", at = @At("RETURN"), cancellable = true)
        private void naturality$listenerWater(CallbackInfoReturnable<Boolean> cir) {
            if ((Object)this instanceof LocalPlayer player && PlayerRotation.terrainActive(player)) {
                wasUnderwater = PlayerRotationRendering.underwaterListener(player);
                cir.setReturnValue(wasUnderwater);
            }
        }
    }

    @Mixin(EntityHitboxDebugRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PlayerRotationDebugMixin {
        @Inject(method = "showHitboxes", at = @At("HEAD"), cancellable = true)
        private void naturality$realHitbox(Entity entity, float partial, boolean server, CallbackInfo ci) {
            if (entity instanceof Player player && PlayerRotation.terrainActive(player)) {
                PlayerRotationRendering.debug(player, partial, server);
                ci.cancel();
            }
        }
    }
}
