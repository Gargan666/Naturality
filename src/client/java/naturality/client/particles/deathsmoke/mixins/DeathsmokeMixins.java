package naturality.client.particles.deathsmoke.mixins;

import naturality.client.particles.deathsmoke.DeathSmokeLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import naturality.client.particles.deathsmoke.DeathSmoke.MobDeathState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Unique;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class DeathsmokeMixins {
    private DeathsmokeMixins() {}

    @Mixin(LivingEntityRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class DeathSmokeLayerMixin<S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
        @Shadow protected abstract boolean addLayer(RenderLayer<S, M> layer);
        @Inject(method="<init>", at=@At("TAIL"))
        private void naturality$layer(EntityRendererProvider.Context context, M model, float shadow, CallbackInfo ci) {
            @SuppressWarnings("unchecked")
            var parent = (net.minecraft.client.renderer.entity.RenderLayerParent<S, M>)(Object)this;
            addLayer(new DeathSmokeLayer<>(parent));
        }
    }

    @Mixin(LivingEntityRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MobDeathRendererMixin {
        @Inject(method="extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at=@At("TAIL"))
        private void naturality$death(LivingEntity entity, LivingEntityRenderState state, float partial, CallbackInfo ci) {
            boolean ghast = entity instanceof Ghast || entity instanceof HappyGhast;
            // Reach the final mask on the last visible tick, before vanilla removes the corpse at 20.
            int progress = entity instanceof Mob && entity.deathTime > 0 && !ghast
                ? Math.round(Math.clamp((entity.deathTime + partial) / 19F, 0F, 1F) * 2047) : 0;
            ((MobDeathState) state).naturality$deathPixels(progress);
            if (ghast && entity.isDeadOrDying() && entity instanceof naturality.entities.ghasts.GhastCrash.GhastCrashBody body
                    && body.naturality$impactTicks() >= 0)
                ((MobDeathState) state).naturality$deathPixels(Math.round(Math.clamp((body.naturality$impactTicks()+partial)/5F,0F,1F)*2047));
            naturality.client.particles.deathsmoke.DeathSmoke.owner(state, entity);
        }

        @Inject(method="getOverlayCoords", at=@At("RETURN"), cancellable=true)
        private static void naturality$encode(LivingEntityRenderState state, float white, CallbackInfoReturnable<Integer> cir) {
            // Overlay U uses only four bits. Keep its tint and V while transporting the mask progress.
            cir.setReturnValue(cir.getReturnValue() | (((MobDeathState) state).naturality$deathPixels() << 4));
        }

        @Inject(method="setupRotations", at=@At("HEAD"))
        private void naturality$liftSpider(LivingEntityRenderState state, com.mojang.blaze3d.vertex.PoseStack pose,
                                          float body, float scale, CallbackInfo ci) {
            if (!((Object)this instanceof net.minecraft.client.renderer.entity.SpiderRenderer<?>) || state.deathTime <= 0) return;
            float turn = (float)Math.PI * (float)Math.sqrt(Math.clamp((state.deathTime-1)/20F*1.6F,0F,1F));
            double lift = state.boundingBoxHeight * (1-Math.cos(turn))*.5
                + state.boundingBoxWidth * Math.abs(Math.sin(turn))*.5;
            pose.translate(0,lift/scale,0);
        }
    }

    @Mixin(LivingEntityRenderState.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MobDeathStateMixin implements MobDeathState {
        @Unique private int naturality$deathPixels;
        public int naturality$deathPixels() { return naturality$deathPixels; }
        public void naturality$deathPixels(int progress) { naturality$deathPixels = progress; }
    }
}
