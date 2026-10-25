package naturality.client.rendering.text.mixins;

import naturality.client.rendering.text.TextBuffers;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional text-renderer compatibility hooks; the outer container is inert. */
public final class TextMixins {
    private TextMixins() {}

    @Mixin(TextFeatureRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ImmediatelyFastTextBoundaryMixin {
        @Inject(method = "renderText", at = @At("HEAD"))
        private static void naturality$beginText(Font font, @Coerce Object glyphRenderer,
                TextFeatureRenderer.Content.Text content, CallbackInfo ci) {
            TextBuffers.beginText(glyphRenderer);
        }
    }
}
