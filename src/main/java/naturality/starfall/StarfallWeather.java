package naturality.starfall;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import naturality.util.LoadedChunks;

/** Bounded per-player weather volume in loaded chunks; no terrain generation requests. */
public final class StarfallWeather {
    private StarfallWeather() {}
    public static void tick(ServerLevel level,int strength) {
        if(strength<=0 || !level.dimension().equals(Level.END))return;
        var random=level.getRandom();
        for(var player:level.players()) {
            if(player.isSpectator())continue;
            double chance=.025+.225*Math.pow(strength/100.0,2);
            if(random.nextDouble()>chance)continue;
            double angle=random.nextDouble()*Math.PI*2, radius=48+random.nextDouble()*28;
            double x=player.getX()+Math.cos(angle)*radius,z=player.getZ()+Math.sin(angle)*radius;
            boolean above=random.nextBoolean();
            // The End's islands generally sit near Y=64. Both streams aim through that band.
            var start=new Vec3(x,above?240+random.nextDouble()*24:-48,z);
            double heading=angle+Math.PI+(random.nextDouble()-.5)*1.2;
            double slope=.35+random.nextDouble()*.3;
            double horizontal=Math.abs(start.y-64)*slope;
            var target=new Vec3(x+Math.cos(heading)*horizontal,64,z+Math.sin(heading)*horizontal);
            if(!LoadedChunks.has(level,BlockPos.containing(start)) || !LoadedChunks.has(level,BlockPos.containing(target)))continue;
            if(!level.getWorldBorder().isWithinBounds(BlockPos.containing(start)))continue;
            // Avoid multiplying the same local shower when players stand together.
            boolean duplicate=false;
            for(var other:level.players())if(other!=player && other.getId()<player.getId() && !other.isSpectator()
                    && other.position().distanceToSqr(start)<player.position().distanceToSqr(start)) {duplicate=true;break;}
            if(duplicate)continue;
            int nearby=level.getEntitiesOfClass(FallingStar.class,new net.minecraft.world.phys.AABB(player.getX()-112,level.getMinY()-64,player.getZ()-112,
                player.getX()+112,level.getMaxY()+32,player.getZ()+112)).size();
            if(nearby>=64)continue;
            var type=random.nextFloat()<.65?StarfallEntities.FIZZLE:StarfallEntities.STAR;
            var star=new FallingStar(type,level);
            star.launch(start,target.subtract(start).normalize().scale(1.4+random.nextDouble()*.8),8+random.nextInt(20));
            level.addFreshEntity(star);
        }
    }
}