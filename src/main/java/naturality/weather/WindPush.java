package naturality.weather;

import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Resolve intrusion before vanilla movement, which cannot resolve existing overlaps. */
public final class WindPush {
    private WindPush() {}
    /** @return true when solid surroundings leave no safe escape this tick. */
    public static boolean resolve(Player player) {
        if(player.isSpectator() || player.noPhysics || !WindShapes.active(player.level()))return false;
        var level=player.level();
        var box=player.getBoundingBox();
        var overlaps=new ArrayList<AABB>();
        for(var pos:BlockPos.betweenClosed(BlockPos.containing(box.minX-1,box.minY-1,box.minZ-1),
                BlockPos.containing(box.maxX+1,box.maxY+1,box.maxZ+1))) {
            if(!naturality.util.LoadedChunks.has(level,pos))continue;
            var state=level.getBlockState(pos);
            if(!WindShapes.eligible(state) || WindShapes.offset(level,pos,state).equals(Vec3.ZERO))continue;
            for(var part:state.getCollisionShape(level,pos).toAabbs()) {
                var world=part.move(pos);
                if(world.intersects(box.deflate(1e-7)))overlaps.add(world);
            }
        }
        if(overlaps.isEmpty())return false;
        double left=0,right=0,north=0,south=0,up=0;
        for(var part:overlaps) {
            left=Math.min(left,part.minX-box.maxX-1e-5);
            right=Math.max(right,part.maxX-box.minX+1e-5);
            north=Math.min(north,part.minZ-box.maxZ-1e-5);
            south=Math.max(south,part.maxZ-box.minZ+1e-5);
            up=Math.max(up,part.maxY-box.minY+1e-5);
        }
        var candidates=new ArrayList<>(java.util.List.of(new Vec3(left,0,0),new Vec3(right,0,0),
            new Vec3(0,0,north),new Vec3(0,0,south),new Vec3(0,up,0)));
        candidates.sort(Comparator.comparingDouble(Vec3::lengthSqr));
        for(var shift:candidates) {
            if(shift.lengthSqr()>2.25)continue;
            var target=box.move(shift);
            if(!level.noCollision(player,target))continue;
            // Do not escape through a wall to reach an otherwise empty destination.
            var swept=box.minmax(target).deflate(1e-7);
            boolean blocked=false;
            for(var shape:level.getBlockCollisions(player,swept))for(var part:shape.toAabbs()) {
                if(part.intersects(swept) && !part.intersects(box))blocked=true;
            }
            if(blocked)continue;
            player.setPos(player.getX()+shift.x,player.getY()+shift.y,player.getZ()+shift.z);
            return false;
        }
        return true;
    }
}
