package naturality.client.mixin;

import naturality.client.portal.end.*;
import net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer;
import net.minecraft.client.renderer.blockentity.state.EndPortalRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractEndPortalRenderer.class)
public class EndPortalBorderExtractMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void naturality$extract(TheEndPortalBlockEntity entity, EndPortalRenderState state,
            float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay breaking, CallbackInfo ci) {
        ((EndPortalBorderAccess) state).naturality$setBorder(EndPortalBorderState.extract(entity, camera));
    }
}
