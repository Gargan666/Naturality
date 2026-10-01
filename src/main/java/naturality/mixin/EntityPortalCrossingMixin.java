package naturality.mixin;
import naturality.portal.*;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Entity.class)
public abstract class EntityPortalCrossingMixin implements PortalCrossingAccess {
    @Unique private @org.jspecify.annotations.Nullable PortalCrossing naturality$crossing;
    public @org.jspecify.annotations.Nullable PortalCrossing naturality$getCrossing(){return naturality$crossing;}
    public void naturality$setCrossing(@org.jspecify.annotations.Nullable PortalCrossing crossing){naturality$crossing=crossing;}
    @Inject(method="baseTick",at=@At("HEAD"))
    private void naturality$crossingTick(CallbackInfo ci){PortalCrossing.tick((Entity)(Object)this);}
}
