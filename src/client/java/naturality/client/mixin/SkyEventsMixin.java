package naturality.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import naturality.client.sky.MeteorShower;
import naturality.client.sky.RainbowRenderer;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class SkyEventsMixin {
    @Unique private @org.jspecify.annotations.Nullable MeteorShower naturality$meteors;
    @Unique private @org.jspecify.annotations.Nullable RainbowRenderer naturality$rainbow;
    @Inject(method = "render", at = @At("HEAD"))
    private void naturality$prepare(com.mojang.renderpearl.api.buffers.GpuBufferSlice fog,
            net.minecraft.client.renderer.state.level.SkyRenderState state, CallbackInfo ci) {
        var effect = naturality$meteors;
        if (effect == null) { effect = new MeteorShower(); naturality$meteors = effect; }
        effect.prepare();
        var rainbow = naturality$rainbow;
        if (rainbow == null) { rainbow = new RainbowRenderer(); naturality$rainbow = rainbow; }
        rainbow.prepare();
    }
    @Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
    private void naturality$events(RenderPass pass, PoseStack pose, float sunAngle, float moonAngle,
            float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci) {
        var effect = naturality$meteors;
        if (effect == null) { effect = new MeteorShower(); naturality$meteors = effect; }
        effect.render(pass, rainBrightness);
        if (naturality$rainbow != null) naturality$rainbow.render(pass, sunAngle);
    }
    @Inject(method = "close", at = @At("HEAD"))
    private void naturality$close(CallbackInfo ci) {
        if (naturality$meteors != null) { naturality$meteors.close(); naturality$meteors = null; }
        if (naturality$rainbow != null) { naturality$rainbow.close(); naturality$rainbow = null; }
    }
}

