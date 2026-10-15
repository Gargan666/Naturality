package naturality.client.mixin;
import naturality.client.entity.GhastTentacleState;
import net.minecraft.client.model.monster.ghast.GhastModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GhastModel.class)
public abstract class GhastTentaclePhysicsMixin {
    @Inject(method="animateTentacles",at=@At("HEAD"),cancellable=true)
    private static void naturality$deathTentacles(EntityRenderState state,ModelPart[] tentacles,CallbackInfo ci) {
        if(!(state instanceof GhastTentacleState physics) || physics.naturality$tentacles()==null) return;
        var pose=physics.naturality$tentacles();
        for(int i=0;i<tentacles.length;i++) {
            tentacles[i].xRot=pose.pitch()[i]; tentacles[i].yRot=0; tentacles[i].zRot=pose.roll()[i];
        }
        ci.cancel();
    }
}
