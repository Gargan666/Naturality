package naturality.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.insanity.Insanity;
import naturality.client.insanity.InsanityState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class InsanityDispatcherMixin {
    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void naturality$extract(Entity entity, float partial, CallbackInfoReturnable<EntityRenderState> cir) {
        Insanity.extract(entity, cir.getReturnValue(), partial);
    }
    @Inject(method = "submit", at = @At("HEAD"))
    private void naturality$submit(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
            PoseStack pose, SubmitNodeCollector collector, CallbackInfo ci) {
        if (!(state instanceof InsanityState insane)) return;
        var proxy = insane.naturality$insanity().hallucination;
        if (proxy != null) ((EntityRenderDispatcher)(Object)this).submit(proxy, camera, x, y, z, pose, collector);
    }
}
