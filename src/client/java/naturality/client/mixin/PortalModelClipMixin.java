package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import naturality.client.portal.*;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelFeatureRenderer.class)
public abstract class PortalModelClipMixin {
    @Unique private PortalClippedVertexConsumer naturality$buffer;
    @ModifyExpressionValue(method="prepareModel",at=@At(value="INVOKE",
        target="Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer;getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer naturality$clip(VertexConsumer buffer,@Local(argsOnly=true) ModelFeatureRenderer.Submit<?> submit) {
        var plane=PortalClippedCollector.plane(submit);
        naturality$buffer=plane==null?null:plane.wrap(buffer);
        return naturality$buffer==null?buffer:naturality$buffer;
    }
    @Inject(method="prepareModel",at=@At("RETURN"))
    private void naturality$finish(CallbackInfo ci) {
        if(naturality$buffer!=null) naturality$buffer.finish();
        naturality$buffer=null;
    }
}
