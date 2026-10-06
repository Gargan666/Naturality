package naturality.test;

import java.util.UUID;
import naturality.starfall.*;
import naturality.weather.*;
import naturality.client.entity.FallingStarRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class StarfallGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
    private static FallingStar launch(net.minecraft.server.level.ServerLevel level,boolean fizzle,Vec3 pos,Vec3 velocity,int life) {
        var star=new FallingStar(fizzle?StarfallEntities.FIZZLE:StarfallEntities.STAR,level);
        star.launch(pos,velocity,life);level.addFreshEntity(star);return star;
    }
    @Override public void runTest(ClientGameTestContext context) {
        var cycle=new EndWeatherCycle(154);
        cycle.set("starfall",100);for(int i=0;i<200;i++)cycle.tick(false);
        check(cycle.state().starfall()==100,"Manual starfall eases to full strength while weather is paused");
        var restored=new EndWeatherCycle(2);restored.restore(cycle.snapshot());
        for(int i=0;i<4000;i++){cycle.tick(true);restored.tick(true);check(cycle.snapshot().equals(restored.snapshot()),"Starfall clocks and random sequence persist exactly");}
        cycle.set("starfall",0);cycle.tick(false);check(cycle.state().starfall()>0,"Shower fades out smoothly");
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("execute in minecraft:the_end run tp @a 0 132 -8 0 0");
            context.waitTicks(40);
            server.runCommand("execute in minecraft:the_end run fill -12 120 -12 12 150 12 air");
            server.runCommand("execute in minecraft:the_end run fill -10 130 -10 10 130 10 end_stone");
            server.runOnServer(s->{
                var level=s.getLevel(Level.END);
                var down=launch(level,false,new Vec3(0,134,0),new Vec3(0,-6,0),30);down.tick();
                check(down.isRemoved(),"Downward star hits terrain");
                var up=launch(level,false,new Vec3(3,127,0),new Vec3(0,6,0),30);up.tick();
                check(up.isRemoved(),"Upward star hits an island underside");
                for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)check(level.getBlockState(new BlockPos(x,130,z)).is(Blocks.END_STONE),"Impact explosions preserve all terrain");
                var distant=launch(level,false,new Vec3(-8,264,0),new Vec3(0,-.6,0),30);
                for(int tick=0;tick<280 && !distant.isRemoved();tick++)distant.tick();
                check(distant.isRemoved() && Math.abs(distant.getY()-131)<.01,"High stars reach the top face of terrain: "+distant.getY());
                var cow=EntityTypes.COW.create(level,EntitySpawnReason.COMMAND);cow.setNoAi(true);
                cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);cow.setHealth(1000);cow.setPos(6,131,0);level.addFreshEntity(cow);
                float health=cow.getHealth();
                var fizzle=launch(level,true,new Vec3(6,135,0),new Vec3(0,-6,0),30);fizzle.tick();
                check(fizzle.isRemoved() && cow.getHealth()==health,"Fizzles burn out before entity impacts without damage");
                var hit=launch(level,false,new Vec3(6,135,0),new Vec3(0,-6,0),30);hit.tick();
                check(hit.isRemoved() && cow.getHealth()<=health-24,"Real stars deal high direct-hit damage");cow.discard();
                var timed=launch(level,true,new Vec3(-5,145,0),new Vec3(0,-.1,0),2);timed.tick();timed.tick();
                check(timed.isRemoved(),"Fizzles burst early without requiring a collision");
                var zero=level.getEntitiesOfClass(FallingStar.class,new net.minecraft.world.phys.AABB(-100,-30,-100,100,160,100)).size();
                for(int i=0;i<100;i++)StarfallWeather.tick(level,0);
                check(level.getEntitiesOfClass(FallingStar.class,new net.minecraft.world.phys.AABB(-100,-30,-100,100,160,100)).size()==zero,"Zero starfall produces no stars");
                var player=level.players().getFirst();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                level.getRandom().setSeed(123);
                for(int i=0;i<1000;i++)StarfallWeather.tick(level,100);
                var shower=level.getEntitiesOfClass(FallingStar.class,new net.minecraft.world.phys.AABB(-112,-64,-112,112,level.getMaxY()+32,112));
                check(shower.size()>10 && shower.size()<=64,"High starfall produces a bounded shower");
                check(shower.stream().anyMatch(e->e.getDeltaMovement().y>0) && shower.stream().anyMatch(e->e.getDeltaMovement().y<0),"Stars spawn both above and below islands");
                check(shower.stream().anyMatch(FallingStar::fizzle) && shower.stream().anyMatch(e->!e.fizzle()),"Weather includes real stars and fizzles");
                for(var star:shower) {
                    double slope=star.getDeltaMovement().horizontalDistance()/Math.abs(star.getDeltaMovement().y);
                    check(slope>=.349 && slope<=.651,"Weather trajectories have a visible slant");
                    check(star.opacity(0)==0,"New stars begin invisible");
                    if(star.fizzle()) {
                        boolean above=star.getDeltaMovement().y<0;
                        for(int tick=0;tick<40 && !star.isRemoved();tick++)star.tick();
                        check(star.isRemoved(),"Weather fizzles burn out early");
                        check(above?star.getY()>180:star.getY()<16,"Fizzles burst far from the island band");
                    }
                }
                shower.forEach(FallingStar::discard);player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                launch(level,false,new Vec3(-2,134,0),new Vec3(.02,-.015,0),30);
                launch(level,true,new Vec3(2,134,0),new Vec3(-.02,-.015,0),28);
            });
            context.waitTicks(12);
            context.runOnClient(c->{
                int found=0;
                try {
                    var field=naturality.client.particle.StarTrailParticle.class.getDeclaredField("attached");field.setAccessible(true);
                    var trails=(java.util.Map<?,?>)field.get(null);
                    for(var entity:c.level.entitiesForRendering())if(entity instanceof FallingStar star) {
                        check(c.getEntityRenderDispatcher().getRenderer(star) instanceof FallingStarRenderer,"Both stars and fizzles use the authored model renderer");
                        var particle=(net.minecraft.client.particle.Particle)trails.get(star);
                        check(particle!=null && particle.isAlive(),"Each tracked star owns a live attached star-trail particle");
                        check(naturality.client.particle.WindParticleControl.immune(particle),"Star trail remains anchored despite weather wind");found++;
                    }
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}
                check(found==2,"Both visual variants are tracked and rendered");
            });
            context.takeScreenshot("starfall-models-and-trails");
        }
    }
}