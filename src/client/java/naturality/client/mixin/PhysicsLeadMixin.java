package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.villager.PhysicsLeadRendering;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class PhysicsLeadMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void naturality$identify(net.minecraft.world.entity.Entity entity, EntityRenderState state,
            float partialTicks, CallbackInfo ci) {
        var leashes = state.leashStates;
        if (leashes == null || !(entity instanceof net.minecraft.world.entity.Leashable leashable)) return;
        var holder = leashable.getLeashHolder();
        if (holder == null) return;
        for (int i = 0; i < leashes.size(); i++)
            PhysicsLeadRendering.identify(leashes.get(i),
                new PhysicsLeadRendering.Key(entity.getId(), holder.getId(), i));
    }
    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitLeash(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/entity/state/EntityRenderState$LeashState;)V"))
    private void naturality$chain(SubmitNodeCollector collector, PoseStack pose,
            EntityRenderState.LeashState state, Operation<Void> original) {
        PhysicsLeadRendering.submit(pose, collector, state);
    }
}
