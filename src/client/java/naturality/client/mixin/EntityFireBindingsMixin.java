package naturality.client.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.List;
import naturality.client.fire.ProceduralFire;
import net.minecraft.client.renderer.feature.*;
import net.minecraft.client.renderer.oit.OitStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTypeFeatureRenderer.class)
public abstract class EntityFireBindingsMixin {
    @Inject(method = "executeGroup", at = @At("HEAD"))
    private void naturality$bind(FeatureFrameContext context, OitStage stage, RenderPass pass, int index,
            List<?> submits, boolean ordered, CallbackInfo ci) {
        if ((Object) this instanceof FlameFeatureRenderer) ProceduralFire.bind(pass);
    }
}
