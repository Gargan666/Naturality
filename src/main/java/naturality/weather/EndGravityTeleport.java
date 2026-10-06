package naturality.weather;

import naturality.util.LoadedChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Searches loaded island columns for collision-free footing on their underside. */
public final class EndGravityTeleport {
    private EndGravityTeleport() {}
    public static boolean seekUnderside(Enderman mob) {
        if(toUnderside(mob,mob.getX(),mob.getZ()))return true;
        for(int i=0;i<8;i++) {
            double x=mob.getX()+mob.getRandom().nextInt(49)-24;
            double z=mob.getZ()+mob.getRandom().nextInt(49)-24;
            if(toUnderside(mob,x,z))return true;
        }
        return false;
    }
    public static boolean toUnderside(Enderman mob,double x,double z) {
        if(!(mob.level() instanceof ServerLevel level) || mob.isPassenger())return false;
        BlockPos column=BlockPos.containing(x,mob.getY(),z);
        if(!LoadedChunks.has(level,column) || !level.getWorldBorder().isWithinBounds(column))return false;
        int top=level.getHeight(Heightmap.Types.MOTION_BLOCKING,column.getX(),column.getZ());
        // Start at the island's top and find its first underside with enough
        // clearance. Reading the column never requests an absent chunk.
        var cursor=new BlockPos.MutableBlockPos(column.getX(),top,column.getZ());
        for(int y=top;y>level.getMinY()+mob.getBbHeight();y--) {
            cursor.setY(y);
            var support=level.getBlockState(cursor);
            if(!support.isFaceSturdy(level,cursor,Direction.DOWN) || support.is(BlockTags.ENDERMAN_DOES_NOT_TELEPORT_TO))continue;
            // Keep subtraction in double precision; float rounding can put the feet inside the ceiling.
            double footY=(double)y-mob.getBbHeight();
            var target=new Vec3(column.getX()+.5,footY,column.getZ()+.5);
            var box=mob.getBoundingBox().move(target.subtract(mob.position()));
            if(!level.noCollision(mob,box) || level.containsAnyLiquid(box))continue;
            var old=mob.position();
            mob.teleportTo(target.x,target.y,target.z);
            mob.setDeltaMovement(Vec3.ZERO);mob.resetFallDistance();mob.setOnGround(true);
            mob.getNavigation().stop();mob.needsSync=true;
            level.broadcastEntityEvent(mob,(byte)46);
            level.gameEvent(GameEvent.TELEPORT,old,GameEvent.Context.of(mob));
            if(!mob.isSilent()) {
                level.playSound(null,old.x,old.y,old.z,SoundEvents.ENDERMAN_TELEPORT,mob.getSoundSource(),1,1);
                mob.playSound(SoundEvents.ENDERMAN_TELEPORT,1,1);
            }
            return true;
        }
        return false;
    }
}
