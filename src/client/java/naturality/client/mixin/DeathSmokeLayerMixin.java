package naturality.client.mixin;

import naturality.client.entity.DeathSmokeLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class DeathSmokeLayerMixin<S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
    @Shadow protected abstract boolean addLayer(RenderLayer<S, M> layer);
    @Inject(method="<init>", at=@At("TAIL"))
    private void naturality$layer(EntityRendererProvider.Context context, M model, float shadow, CallbackInfo ci) {
        @SuppressWarnings("unchecked")
        var parent = (net.minecraft.client.renderer.entity.RenderLayerParent<S, M>)(Object)this;
        addLayer(new DeathSmokeLayer<>(parent));
    }
}
