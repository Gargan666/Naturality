package naturality.client.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.List;
import naturality.client.fire.ProceduralFire;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.QuadParticleFeatureRenderer;
import net.minecraft.client.renderer.oit.OitStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(QuadParticleFeatureRenderer.class)
public abstract class FireParticleBindingsMixin {
    @Inject(method = "executeGroup", at = @At("HEAD"))
    private void naturality$fireHeat(FeatureFrameContext context, OitStage stage, RenderPass pass, int index,
            List<QuadParticleFeatureRenderer.Submit> submits, boolean ordered, CallbackInfo ci) {
        ProceduralFire.bind(pass);
    }
}
