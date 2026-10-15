package naturality.client.api.geometry.mixins;

import com.mojang.blaze3d.vertex.*;
import naturality.client.api.geometry.ModelCapture;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.Cube.class)
@org.jspecify.annotations.NullMarked
public abstract class FlatModelCaptureMixin {
    @Inject(method="compile",at=@At("HEAD"),cancellable=true)
    private void naturality$alphaPlane(PoseStack.Pose pose,VertexConsumer buffer,int light,int overlay,int color,CallbackInfo ci) {
        if(buffer instanceof ModelCapture capture && capture.flatCube((ModelPart.Cube)(Object)this,pose))ci.cancel();
    }
}
