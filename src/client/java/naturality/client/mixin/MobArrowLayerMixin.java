package naturality.client.mixin;

import naturality.client.entity.EmbeddedArrowState;
import naturality.client.entity.MobArrowLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class MobArrowLayerMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
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
    }
}
