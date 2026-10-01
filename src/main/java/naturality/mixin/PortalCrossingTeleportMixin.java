package naturality.mixin;
import naturality.portal.PortalCrossing;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PortalProcessor.class)
public abstract class PortalCrossingTeleportMixin {
    @Inject(method="getPortalDestination",at=@At("RETURN"),cancellable=true)
    private void naturality$arrival(ServerLevel level,Entity entity,
            CallbackInfoReturnable<net.minecraft.world.level.portal.TeleportTransition> ci) {
        var crossing=PortalCrossing.get(entity);
        if(portal==Blocks.NETHER_PORTAL && crossing!=null && ci.getReturnValue()!=null)
            ci.setReturnValue(naturality.portal.PortalArrival.prepare(ci.getReturnValue(),crossing));
    }
    @SuppressWarnings("null") @Shadow @Final private Portal portal;
    @Shadow private int portalTime;
    @Shadow private boolean insidePortalThisTick;
    @Inject(method="processPortalTeleportation",at=@At("HEAD"),cancellable=true)
    private void naturality$crossing(ServerLevel level,Entity entity,boolean allowed,CallbackInfoReturnable<Boolean> ci){
        if(portal!=Blocks.NETHER_PORTAL || !naturality.config.GameplaySettings.physicalPortalEntry(level)) return;
        PortalCrossing c=PortalCrossing.get(entity);
        insidePortalThisTick=false;
        // Voxel contact can create a processor before the hitbox reaches the slab.
        // Never fall back to vanilla's zero-delay travel for mobs/creative players.
        if(c==null) {
            portalTime=0;
            ci.setReturnValue(false);
            return;
        }
        portalTime++;
        ci.setReturnValue(allowed && c.valid(entity) && c.progress(entity.getBoundingBox())>=1);
    }
}
