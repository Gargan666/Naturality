package naturality.client.sky.overworld.sunbeams.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import naturality.client.sky.overworld.sunbeams.SunBeams;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import naturality.client.sky.overworld.sunbeams.SunBeams.SunLensFlare;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class SunbeamsMixins {
    private SunbeamsMixins() {}

    @org.jspecify.annotations.NullMarked
    @Mixin(SkyRenderer.class)
    public static abstract class SunBeamsMixin {
        @SuppressWarnings("null") @Shadow @Final private TextureAtlas celestialsAtlas;
        @Unique private @org.jspecify.annotations.Nullable SunBeams naturality$beams;
        @Unique private float naturality$sunAngle;

        @Inject(method = "render", at = @At("HEAD"))
        private void naturality$angle(GpuBufferSlice fog, SkyRenderState state, CallbackInfo ci) {
            naturality$sunAngle = state.sunAngle;
        }

        @Inject(method = "renderSun", at = @At("HEAD"))
        private void naturality$beams(RenderPass pass, float rainBrightness, PoseStack pose, CallbackInfo ci) {
            var settings = naturality.config.live.NaturalityConfig.get().effects;
            if (!settings.sunBeams && !settings.cameraFlare) return;
            var effect = naturality$beams;
            if (effect == null) { effect = new SunBeams(); naturality$beams = effect; }
            effect.render(pass, pose, naturality$sunAngle, rainBrightness,
                celestialsAtlas.getSprite(Identifier.withDefaultNamespace("sun")));
        }

        @Inject(method = "close", at = @At("HEAD"))
        private void naturality$close(CallbackInfo ci) {
            if (naturality$beams != null) {
                naturality$beams.close();
                naturality$beams = null;
            }
        }
    }

    @org.jspecify.annotations.NullMarked
    @Mixin(GameRenderer.class)
    public static abstract class SunLensFlareOverlayMixin {
        @Inject(method = "render", at = @At("HEAD"))
        private void naturality$resetFlare(CallbackInfo ci) {
            SunLensFlare.beginFrame();
        }

        @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
        private void naturality$cameraFlare(CallbackInfo ci) {
            SunLensFlare.renderOverlay();
        }
    }

    @org.jspecify.annotations.NullMarked
    @Mixin(SpriteContents.class)
    public interface SunSpriteAccess {
        @Accessor("originalImage") NativeImage naturality$sunImage();
    }
}
