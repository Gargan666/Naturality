package naturality.client.mixin;

import java.util.List;
import naturality.client.fire.EntityFireRenderer;
import net.minecraft.client.renderer.feature.*;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlameFeatureRenderer.class)
public abstract class EntityFireRenderMixin extends RenderTypeFeatureRenderer<FlameFeatureRenderer.Submit> {
    @Inject(method = "buildGroup", at = @At("HEAD"), cancellable = true)
    private void naturality$boxFire(FeatureFrameContext context, List<FlameFeatureRenderer.Submit> submits, CallbackInfo ci) {
        var buffer = getVertexBuilder(EntityFireRenderer.LAYER);
        for (var submit : submits) {
            var state = (naturality.client.fire.EntityFireState) submit.entityRenderState();
            var sprite = context.atlasManager().get(state.naturality$soulFire()
                ? new SpriteId(naturality.client.AtlasLocations.BLOCKS, Identifier.withDefaultNamespace("block/soul_fire_0"))
                : ModelBakery.FIRE_0);
            EntityFireRenderer.emit(submit, buffer, sprite);
        }
        ci.cancel();
    }
}
