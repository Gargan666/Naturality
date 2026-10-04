package naturality.test;
import naturality.weather.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

public final class WindShapesGameTest implements FabricClientGameTest {
    private static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    private static void shapes(Level level) {
        var sheltered=new BlockPos(8,120,0);
        check(WindShapes.offset(level,sheltered,level.getBlockState(sheltered)).equals(Vec3.ZERO),"Indoor leaves keep their original hitbox");
        var pos=new BlockPos(0,120,0);var state=level.getBlockState(pos);
        var offset=WindShapes.offset(level,pos,state);
        check(offset.x>.3,"Strong wind must move the leaf hitbox: "+offset+" weather="+WeatherSystem.state(level)+" sky="+level.getBrightness(LightLayer.SKY,pos)+" state="+state);
        var outline=state.getShape(level,pos).bounds();
        var collision=state.getCollisionShape(level,pos).bounds();
        check(Math.abs(outline.minX-offset.x)<1e-6 && Math.abs(collision.minX-offset.x)<1e-6,"Outline and cached collision must agree without double offset");
        check(Math.abs(outline.getXsize()-1)<1e-6 && Math.abs(outline.getYsize()-1)<1e-6,"Wind translates without stretching");
        var snowPos=pos.above();var snow=level.getBlockState(snowPos);
        check(WindShapes.offset(level,snowPos,snow).equals(offset),"Snow follows its supporting leaf");
        check(Math.abs(snow.getShape(level,snowPos).bounds().minX-offset.x)<1e-6,"Snow outline moves exactly once");
        check(Math.abs(snow.getCollisionShape(level,snowPos).bounds().minX-offset.x)<1e-6,"Snow collision follows the cap");
        var edge=new AABB(1.1,120.2,.3,1.2,120.8,.7);
        check(level.getBlockCollisions(null,edge).iterator().hasNext(),"Collision broad phase must find a leaf displaced into the neighboring cell");
        var oldEdge=new AABB(.01,120.2,.3,.1,120.8,.7);
        check(!level.getBlockCollisions(null,oldEdge).iterator().hasNext(),"Old leaf bounds must no longer collide");
        var hit=level.clip(new ClipContext(new Vec3(1.15,120.5,-2),new Vec3(1.15,120.5,2),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()));
        check(hit.getType()==HitResult.Type.BLOCK && hit.getBlockPos().equals(pos),"Picking outside the saved cell must return the leaf owner");
        var severe=new WeatherState(0,100,50,0);
        check(WindShapes.broad(severe,0,pos,true).equals(WindShapes.broad(severe,81,pos,true)),"High-wind hitboxes must not shake or oscillate");
        var mild=new WeatherState(0,50,50,0);
        check(!WindShapes.broad(mild,0,pos,true).equals(WindShapes.broad(mild,20,pos,true)),"Ordinary hitboxes follow broad sway");
    }
    private static void climbing(net.minecraft.world.entity.LivingEntity entity) {
        var pos=new BlockPos(4,120,0);var state=entity.level().getBlockState(pos);
        var shape=state.getShape(entity.level(),pos).bounds().move(pos);
        entity.setPos(shape.maxX+.25,120,shape.getCenter().z);
        check(entity.onClimbable(),"Vines must be climbable at their displaced surface outside the saved block");
        entity.setPos(4.05,120,shape.getCenter().z);
        check(!entity.onClimbable(),"The old vine position must not remain climbable");
    }
    private static void pushing(net.minecraft.world.entity.player.Player player) {
        var pos=new BlockPos(0,120,0);
        var leaf=player.level().getBlockState(pos).getCollisionShape(player.level(),pos).bounds().move(pos);
        double half=player.getBbWidth()/2.0;
        // Simulate a leaf advancing into the player's right-side contact by 0.04 blocks.
        player.setPos(leaf.maxX+half-.04,120.1,.5);
        double before=player.getX();
        player.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(-.15,0,0));
        check(player.getX()>before,"Intruding leaves must push the player out before inward walking");
        check(player.level().noCollision(player,player.getBoundingBox()),"Player must finish outside the moving leaf");
        player.setPos(leaf.minX-half+.04,120.1,.5);
        player.move(net.minecraft.world.entity.MoverType.SELF,Vec3.ZERO);
        check(player.getBoundingBox().maxX<=leaf.minX,"Stationary players must be pushed out on the opposite side too");
        player.setPos(leaf.maxX+half+.05,120.1,.5);
        before=player.getX();
        player.move(net.minecraft.world.entity.MoverType.SELF,Vec3.ZERO);
        check(player.getX()==before,"Nearby players without overlap must not be moved");
    }
    @Override public void runTest(ClientGameTestContext context) {
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("gamerule minecraft:advance_weather true");
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runCommand("setblock 0 120 0 oak_leaves[persistent=true]");
            server.runCommand("setblock 0 121 0 snow[layers=4]");
            server.runCommand("setblock 4 120 0 vine[south=true]");
            server.runCommand("fill 7 119 -1 9 123 1 stone hollow");
            server.runCommand("setblock 8 120 0 oak_leaves[persistent=true]");
            server.runCommand("tp @a 0 125 0");
            server.runOnServer(s -> {
                var profile=new WeatherProfile(true);
                profile.overrideWind=profile.overrideDirection=profile.overrideRain=profile.overrideTemperature=true;
                profile.wind=100;profile.direction=0;profile.rain=0;profile.temperature=0;
                WeatherWorldData.get(s).setProfile("minecraft:overworld",profile);
            });
            world.getConnection().waitForClientboundPackets();context.waitTicks(420);
            context.waitFor(c -> WeatherSystem.state(c.level)!=null && WeatherSystem.state(c.level).wind()==100 && Math.abs(WeatherSystem.state(c.level).direction())<.01);
            server.runOnServer(s -> { shapes(s.overworld()); pushing(s.getPlayerList().getPlayers().getFirst()); climbing(s.getPlayerList().getPlayers().getFirst()); });
            context.runOnClient(c -> { shapes(c.level); pushing(c.player); climbing(c.player); });
        }
    }
}
