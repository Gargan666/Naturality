package naturality.client.fluid;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import naturality.config.NaturalityConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;

/** Tracks actual descent because vanilla clears fallDistance before the water-entry effect. */
public final class LavaEntrySplash {
    private record Fall(double y,double distance,boolean water) {}
    private static final Map<Integer,Fall> FALLS=new HashMap<>();
    private static @org.jspecify.annotations.Nullable ClientLevel level;
    public static int lastSplashCount;
    public static void tick(Minecraft client) {
        lastSplashCount=0;
        if(level!=client.level) {level=client.level;FALLS.clear();}
        var level = LavaEntrySplash.level;
        if(level==null || !NaturalityConfig.get().liquids.lava) {FALLS.clear();LavaImpactColumn.clear();return;}
        if(client.isPaused())return;
        var seen=new HashSet<Integer>();
        for(var entity:level.entitiesForRendering()) {
            if(!(entity instanceof LivingEntity) && !(entity instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat) || entity.isSpectator())continue;
            seen.add(entity.getId());
            var old=FALLS.get(entity.getId());
                        var contact=net.minecraft.core.BlockPos.containing(entity.getX(),entity.getY()+.05,entity.getZ());
            var fluid=level.getFluidState(contact);
            boolean water=fluid.is(net.minecraft.tags.FluidTags.LAVA) && entity.getY()<contact.getY()+fluid.getHeight(level,contact);
            double descent=old==null?0:old.y-entity.getY();
            // Ignore teleports; normal falling motion must account for the displacement.
            boolean continuous=descent>=0 && descent<=Math.max(3,Math.abs(entity.getDeltaMovement().y)*2+1);
            double distance=old!=null && !old.water && continuous?old.distance+descent:0;
            if(water && old!=null && !old.water && distance>5 && !entity.isSilent() && !entity.isPassenger()) {

                lastSplashCount++;
                LavaImpactColumn.spawn(level,entity,distance);
            }
            if(water || (entity.onGround() && Math.abs(descent)<.001) || entity.isPassenger() || (entity instanceof LivingEntity living && living.onClimbable()) || !continuous)distance=0;
            FALLS.put(entity.getId(),new Fall(entity.getY(),distance,water));
        }
        FALLS.keySet().retainAll(seen);
    }
}







