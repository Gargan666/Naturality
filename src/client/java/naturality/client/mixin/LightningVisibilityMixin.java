package naturality.client.mixin;
import naturality.client.weather.DistantLightningClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(LevelExtractor.class)
public abstract class LightningVisibilityMixin {
    @Inject(method="isEntityVisible",at=@At("HEAD"),cancellable=true)
    private void naturality$visible(Entity entity,Frustum frustum,double x,double y,double z,float partial,long fade,CallbackInfoReturnable<Boolean> cir) {
        if(entity instanceof LightningBolt)cir.setReturnValue(true);
    }
    @Inject(method="extractVisibleEntities",at=@At("RETURN"))
    private void naturality$distant(Camera camera,Frustum frustum,DeltaTracker delta,LevelRenderState output,CallbackInfo ci) {
        DistantLightningClient.extract(output,delta.getGameTimeDeltaPartialTick(false));
    }
}
