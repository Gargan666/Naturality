package naturality.client.portals.nether.travel.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.portals.nether.travel.PortalCrossingClient;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import naturality.client.portals.nether.travel.PortalCrossingClient.PortalClippedCollector;
import naturality.client.portals.nether.travel.PortalCrossingClient.PortalClippedSubmit;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.injection.At;
import naturality.client.portals.nether.travel.PortalCrossingClient.PortalClippedVertexConsumer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.VertexConsumer;
import naturality.client.portals.nether.glow.PortalGlowGeometry.PortalTextureBrightness;
import naturality.client.portals.nether.travel.PortalCrossingClient.PortalOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.Blocks;
import naturality.portals.nether.travel.PortalCrossing;
import net.minecraft.client.player.LocalPlayer;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class TravelMixins {
    private TravelMixins() {}

    @Mixin(EntityRenderDispatcher.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PortalCrossingRenderMixin {
        @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method="submit")
        private void naturality$clipBody(EntityRenderState state,CameraRenderState camera,double x,double y,double z,
                PoseStack poses,SubmitNodeCollector collector,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
            original.call(state,camera,x,y,z,poses,PortalCrossingClient.clip(state,camera.pos,collector));
        }
        @Inject(method="extractEntity",at=@At("RETURN"))
        private void naturality$entrySide(Entity entity,float partial,CallbackInfoReturnable<EntityRenderState> ci){
            PortalCrossingClient.remember(entity,ci.getReturnValue());
        }
        @Inject(method="submit",at=@At("HEAD"),cancellable=true)
        private void naturality$hideExit(EntityRenderState state,CameraRenderState camera,double x,double y,double z,
                PoseStack poses,SubmitNodeCollector collector,CallbackInfo ci){
            if(PortalCrossingClient.hidden(state,camera.pos)) ci.cancel();
        }
    }

    @Mixin({ModelFeatureRenderer.Submit.class,ItemFeatureRenderer.Submit.class})
    @org.jspecify.annotations.NullMarked
    public static abstract class PortalFeatureSubmitMixin implements PortalClippedSubmit {
        @Unique private PortalClippedCollector.@org.jspecify.annotations.Nullable Plane naturality$clipPlane;
        @Override public PortalClippedCollector.@org.jspecify.annotations.Nullable Plane naturality$clipPlane() { return naturality$clipPlane; }
        @Override public void naturality$clipPlane(PortalClippedCollector.@org.jspecify.annotations.Nullable Plane plane) { naturality$clipPlane=plane; }
        @Inject(method="<init>",at=@At("RETURN"))
        private void naturality$plane(CallbackInfo ci) {PortalClippedCollector.remember(this);}
    }

    @Mixin(ItemFeatureRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PortalItemClipMixin {
        @WrapOperation(method={"prepareMainSubmit","prepareOutlineSubmit"},at=@At(value="INVOKE",
            target="Lcom/mojang/blaze3d/vertex/VertexConsumer;putBakedQuad(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"))
        private void naturality$quad(VertexConsumer buffer,PoseStack.Pose pose,BakedQuad quad,QuadInstance instance,
            Operation<Void> original,@Local(argsOnly=true) ItemFeatureRenderer.Submit submit) {
            var plane=PortalClippedCollector.plane(submit);
            if(plane==null) {original.call(buffer,pose,quad,instance);return;}
            var clipped=plane.wrap(buffer);original.call(clipped,pose,quad,instance);clipped.finish();
        }
        @WrapOperation(method="prepareMainSubmit",at=@At(value="INVOKE",
            target="Lcom/mojang/blaze3d/vertex/VertexConsumer;putBakedQuadWithGlint(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)V"))
        private void naturality$glint(VertexConsumer buffer,PoseStack.Pose pose,BakedQuad quad,QuadInstance instance,PoseStack.Pose decal,
            Operation<Void> original,@Local(argsOnly=true) ItemFeatureRenderer.Submit submit) {
            var plane=PortalClippedCollector.plane(submit);
            if(plane==null) {original.call(buffer,pose,quad,instance,decal);return;}
            var clipped=plane.wrap(buffer);original.call(clipped,pose,quad,instance,decal);clipped.finish();
        }
    }

    @Mixin(ModelFeatureRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PortalModelClipMixin {
        @Unique private @org.jspecify.annotations.Nullable PortalClippedVertexConsumer naturality$buffer;
        @ModifyExpressionValue(method="prepareModel",at=@At(value="INVOKE",
            target="Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer;getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
        private VertexConsumer naturality$clip(VertexConsumer buffer,@Local(argsOnly=true) ModelFeatureRenderer.Submit<?> submit) {
            var plane=PortalClippedCollector.plane(submit);
            naturality$buffer=plane==null?null:plane.wrap(buffer);
            return naturality$buffer==null?buffer:naturality$buffer;
        }
        @Inject(method="prepareModel",at=@At("RETURN"))
        private void naturality$finish(CallbackInfo ci) {
            if(naturality$buffer!=null) naturality$buffer.finish();
            naturality$buffer=null;
        }
    }

    @Mixin(Hud.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PortalOverlayMixin {
        @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method="extractCameraOverlays",
            at=@At(value="INVOKE",target="Lnet/minecraft/util/Mth;lerp(FFF)F",ordinal=1))
        private float naturality$coverNearPlane(float alpha) {
            return naturality.config.gameplay.GameplaySettings.clientPhysicalPortalEntry() ? PortalOverlay.cameraOpacity(alpha) : alpha;
        }
        @Inject(method="extractPortalOverlay",at=@At("HEAD"),cancellable=true)
        private void naturality$overlay(GuiGraphicsExtractor graphics,float alpha,CallbackInfo ci){
            if (!naturality.config.gameplay.GameplaySettings.clientPhysicalPortalEntry()) return;
            var client=Minecraft.getInstance();
            int range=PortalTextureBrightness.packedRange(client);
            float min=(range&65535)/32767F,max=(range>>>16)/32767F;
            var sprite=client.getModelManager().getBlockStateModelSet().getParticleMaterial(Blocks.NETHER_PORTAL.defaultBlockState()).sprite();
            graphics.blitSprite(PortalOverlay.PIPELINE,sprite,0,0,graphics.guiWidth(),graphics.guiHeight(),ARGB.colorFromFloat(1,alpha,min,max));
            ci.cancel();
        }
    }

    @Mixin(LocalPlayer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PortalOverlayProgressMixin {
        @Inject(method="handlePortalTransitionEffect",at=@At("TAIL"))
        private void naturality$progress(boolean active,CallbackInfo ci){
            if (!naturality.config.gameplay.GameplaySettings.clientPhysicalPortalEntry()) return;
            var player=(LocalPlayer)(Object)this;
            var crossing=PortalCrossing.get(player);
            float previous=player.oPortalEffectIntensity;
            double distance=crossing==null?0:crossing.signed(player.getEyePosition().x,player.getEyePosition().z);
            double half=player.getBbWidth()/2.0;
            float target=crossing!=null && crossing.valid(player)
                ? Math.max(0.0125F,(float)Math.clamp((half-distance)/Math.max(0.001,half-0.1),0,1)):0;
            player.portalEffectIntensity=target>0?target:Math.max(0,previous-0.1F);
            if(player.portalEffectIntensity<0.00001F) player.portalEffectIntensity=0;
        }
    }
}
