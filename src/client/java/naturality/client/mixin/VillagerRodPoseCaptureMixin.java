package naturality.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.villager.VillagerVisualState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class VillagerRodPoseCaptureMixin {
    @Inject(method = "submit", at = @At("HEAD"))
    private void naturality$beginCastRender(LivingEntityRenderState state, PoseStack poseStack,
            SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (state instanceof VillagerRenderState villager) {
            var visual = (VillagerVisualState)villager;
            visual.naturality$basePoseInverse().set(poseStack.last().pose()).invert();
            visual.naturality$setRodTipValid(false);
        }
        if (state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState player)
            naturality.client.villager.PlayerRodTips.roots.put(player,
                new org.joml.Matrix4f(poseStack.last().pose()).invert());
    }
}

