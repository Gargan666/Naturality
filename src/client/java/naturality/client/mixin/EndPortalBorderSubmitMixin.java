package naturality.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.portal.end.EndPortalBorderAccess;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.blockentity.state.EndPortalRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TheEndPortalRenderer.class)
public class EndPortalBorderSubmitMixin {
    @Inject(method = "submit", at = @At("TAIL"))
    private void naturality$submit(EndPortalRenderState state, PoseStack pose, SubmitNodeCollector collector,
            CameraRenderState camera, CallbackInfo ci) {
        var border = ((EndPortalBorderAccess) state).naturality$getBorder();
        if (border != null) border.submit(collector);
    }
}
