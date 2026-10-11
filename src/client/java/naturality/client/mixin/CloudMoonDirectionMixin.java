package naturality.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import naturality.client.cloud.CloudMoonLighting;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class CloudMoonDirectionMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void naturality$reset(CallbackInfo ci) { CloudMoonLighting.beginSky(); }

    @Inject(method = "renderMoon", at = @At("HEAD"))
    private void naturality$moon(RenderPass pass, MoonPhase phase, float brightness, PoseStack pose, CallbackInfo ci) {
        CloudMoonLighting.capture(pose);
    }
}
