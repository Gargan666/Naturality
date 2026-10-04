package naturality.client.mixin;

import java.util.List;
import naturality.client.shadow.BlockGridShadow;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.feature.ShadowFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShadowFeatureRenderer.class)
public abstract class EntityShadowRenderMixin extends RenderTypeFeatureRenderer<ShadowFeatureRenderer.Submit> {
    @Inject(method = "buildGroup", at = @At("HEAD"), cancellable = true)
    private void naturality$gridShadow(FeatureFrameContext context, List<ShadowFeatureRenderer.Submit> submits, CallbackInfo ci) {
        // Resolve every texture before emitting so a failed resource load can use vanilla safely.
        for (var submit : submits) if (BlockGridShadow.texture(submit.radius()) == null) return;
        for (var submit : submits) {
            BlockGridShadow.emit(submit, getVertexBuilder(BlockGridShadow.texture(submit.radius())));
        }
        ci.cancel();
    }
}
