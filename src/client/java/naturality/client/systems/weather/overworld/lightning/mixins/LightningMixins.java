package naturality.client.systems.weather.overworld.lightning.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.client.systems.weather.overworld.lightning.DistantLightningClient.ThunderAudio;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.systems.weather.overworld.lightning.DistantLightningClient.LightningRendering;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.client.renderer.entity.state.LightningBoltRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import naturality.client.systems.weather.overworld.lightning.DistantLightningClient;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class LightningMixins {
    private LightningMixins() {}

    @Mixin(LightningBolt.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class LightningAudioMixin {
        @org.spongepowered.asm.mixin.Unique private boolean naturality$impactAnnounced;
        @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
        private void naturality$thunder(Level level, double x, double y, double z, SoundEvent sound,
                SoundSource source, float volume, float pitch, boolean delay, Operation<Void> original) {
            if (level instanceof ClientLevel client)
                ThunderAudio.schedule(client, new Vec3(x,y,z), sound, source, volume, pitch);
            else original.call(level,x,y,z,sound,source,volume,pitch,delay);
        }
        @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;setSkyFlashTime(I)V"))
        private void naturality$surfaceFlash(Level level, int ticks, Operation<Void> original) {
            if(!(level instanceof ClientLevel)) {original.call(level,ticks);return;}
            if(ThunderAudio.audible()) {
                if(!naturality$impactAnnounced){naturality.client.systems.weather.overworld.lightning.DistantLightningClient.LightningFlashes.strike();naturality$impactAnnounced=true;}
                // The impact overlay owns the timing; suppress vanilla multi-tick/repeated sky flashes.
            }
        }
    }

    @Mixin(Hud.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class LightningFlashOverlayMixin {
        @Inject(method="extractRenderState",at=@At("HEAD"))
        private void naturality$flash(GuiGraphicsExtractor graphics,DeltaTracker delta,CallbackInfo ci) {
            naturality.client.systems.weather.overworld.lightning.DistantLightningClient.LightningFlashes.extract(graphics);
        }
    }

    @Mixin(LightningBoltRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class LightningRendererMixin {
        @WrapOperation(method="submit",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/rendertype/RenderTypes;lightning()Lnet/minecraft/client/renderer/rendertype/RenderType;"))
        private RenderType naturality$unfogged(Operation<RenderType> original) { return LightningRendering.TYPE; }
        @WrapMethod(method="submit")
        private void naturality$distantWidth(LightningBoltRenderState state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera,Operation<Void> original) {
            float width=(float)Math.max(1,Math.hypot(state.x-camera.pos.x,state.z-camera.pos.z)/256);
            pose.pushPose();pose.scale(width,1,width);
            try { original.call(state,pose,collector,camera); } finally { pose.popPose(); }
        }
    }

    @Mixin(LevelExtractor.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class LightningVisibilityMixin {
        @Inject(method="isEntityVisible",at=@At("HEAD"),cancellable=true)
        private void naturality$visible(Entity entity,Frustum frustum,double x,double y,double z,float partial,long fade,CallbackInfoReturnable<Boolean> cir) {
            if(entity instanceof LightningBolt)cir.setReturnValue(true);
        }
        @Inject(method="extractVisibleEntities",at=@At("RETURN"))
        private void naturality$distant(Camera camera,Frustum frustum,DeltaTracker delta,LevelRenderState output,CallbackInfo ci) {
            DistantLightningClient.extract(output,delta.getGameTimeDeltaPartialTick(false));
        }
    }
}
