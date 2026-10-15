package naturality.client.mixin;
import java.util.function.Function;
import net.minecraft.client.model.Model;

import net.minecraft.client.renderer.rendertype.RenderType;

import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Model.class)
public abstract class CrystalVisualMaterialMixin {
    @Inject(method="renderType(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;",at=@At("HEAD"),cancellable=true)
    private void naturality$cull(Identifier texture,CallbackInfoReturnable<RenderType> cir) {
        if((Object)this instanceof naturality.client.entity.CrystalCoreModel) cir.setReturnValue(naturality.client.entity.CrystalCoreModel.LAYER);
    }
    @Inject(method="renderType()Ljava/util/function/Function;",at=@At("HEAD"),cancellable=true)
    private void naturality$cullFactory(CallbackInfoReturnable<Function<Identifier,RenderType>> cir) {
        if((Object)this instanceof naturality.client.entity.CrystalCoreModel) cir.setReturnValue(_ -> naturality.client.entity.CrystalCoreModel.LAYER);
    }
}
