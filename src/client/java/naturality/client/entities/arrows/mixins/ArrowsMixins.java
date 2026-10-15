package naturality.client.entities.arrows.mixins;

import naturality.client.entities.arrows.MobArrowLayer.EmbeddedArrowState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import naturality.client.entities.arrows.MobArrowLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class ArrowsMixins {
    private ArrowsMixins() {}

    @Mixin(LivingEntityRenderState.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EmbeddedArrowStateMixin implements EmbeddedArrowState {
        @Unique private int naturality$arrowCount;
        @Unique private int naturality$arrowSeed;
        @Unique private java.util.List<naturality.client.entities.arrows.MobArrowLayer.MobArrowImpacts.Impact> naturality$impacts = java.util.List.of();
        @Unique private final org.joml.Matrix4f naturality$origin = new org.joml.Matrix4f();
        public java.util.List<naturality.client.entities.arrows.MobArrowLayer.MobArrowImpacts.Impact> naturality$impacts() { return naturality$impacts; }
        public void naturality$setImpacts(java.util.List<naturality.client.entities.arrows.MobArrowLayer.MobArrowImpacts.Impact> hits) { naturality$impacts = hits; }
        public org.joml.Matrix4f naturality$origin() { return naturality$origin; }
        public int naturality$arrowCount() { return naturality$arrowCount; }
        public void naturality$setArrowCount(int count) { naturality$arrowCount = count; }
        public int naturality$arrowSeed() { return naturality$arrowSeed; }
        public void naturality$setArrowSeed(int seed) { naturality$arrowSeed = seed; }
    }

    @Mixin(LivingEntityRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class MobArrowLayerMixin<T extends @org.jspecify.annotations.NonNull LivingEntity, S extends @org.jspecify.annotations.NonNull LivingEntityRenderState, M extends @org.jspecify.annotations.NonNull EntityModel<? super S>> {
        @Shadow protected abstract boolean addLayer(RenderLayer<S, M> layer);

        @Inject(method = "<init>", at = @At("TAIL"))
        private void naturality$addArrows(EntityRendererProvider.Context context, M model, float shadow, CallbackInfo ci) {
            // Mixin's target implements the same RenderLayerParent contract.
            @SuppressWarnings("unchecked")
            var parent = (net.minecraft.client.renderer.entity.RenderLayerParent<S, M>)(Object)this;
            addLayer(new MobArrowLayer<>(parent, context));
        }

        @Inject(method = "extractRenderState", at = @At("TAIL"))
        private void naturality$extractArrows(T entity, S state, float partialTicks, CallbackInfo ci) {
            ((EmbeddedArrowState)state).naturality$setArrowCount(entity.getArrowCount());
            ((EmbeddedArrowState)state).naturality$setArrowSeed(entity.getId());
            ((EmbeddedArrowState)state).naturality$setImpacts(naturality.client.entities.arrows.MobArrowLayer.MobArrowImpacts.impacts(entity));
        }

        @Inject(method = "submit", at = @At("HEAD"))
        private void naturality$origin(S state, com.mojang.blaze3d.vertex.PoseStack stack,
                net.minecraft.client.renderer.SubmitNodeCollector collector,
                net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
            ((EmbeddedArrowState)state).naturality$origin().set(stack.last().pose());
        }
    }
}
