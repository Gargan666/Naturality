package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import naturality.client.breaking.BreakingTextures;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.ModelBakery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SubmitNodeCollection.class)
public abstract class BreakingSubmitMixin {
    @WrapOperation(method = {"submitBreakingBlockModel", "submitCrumblingOverlay"},
        at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"))
    private Object naturality$pattern(List<?> list, int stage, Operation<Object> original) {
        Object fallback = original.call(list, stage);
        if ((list == ModelBakery.DESTROY_TYPES || list == ModelBakery.DESTROY_TYPES_OIT)
                && fallback instanceof RenderType type) {
            return BreakingTextures.current(stage, list == ModelBakery.DESTROY_TYPES_OIT, type);
        }
        return fallback;
    }
}
