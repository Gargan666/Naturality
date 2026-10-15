package naturality.test;
import naturality.client.entity.ZombieWalk;
import naturality.client.entity.ZombieWalkState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
public final class ZombieWalkGameTest implements FabricClientGameTest {
    public void runTest(ClientGameTestContext context) {
        var motion=new ZombieWalk.Motion();
        for(int i=0;i<60;i++) motion.tick(.2);
        var left=motion.pose(1,(float)(Math.PI/2/.6662));
        var right=motion.pose(1,(float)(3*Math.PI/2/.6662));
        if(left.x<6 || left.x>10 || left.y<=0 || right.y>=0 || Math.abs(left.y)>1.8)
            throw new AssertionError("Bounded forward lean and alternating gait sway");
        float moving=motion.pose(1,2).x;
        motion.tick(0);
        if(Math.abs(motion.pose(0,2).x-moving)>.00001 || motion.pose(1,2).x<=0)
            throw new AssertionError("Momentum loss must ease from the previous pose");
        float last=motion.pose(1,2).x;
        for(int i=0;i<60;i++) {
            motion.tick(0);float current=motion.pose(1,2).x;
            if(current>last+.00001) throw new AssertionError("Stopped or blocked lean must decay monotonically");
            last=current;
        }
        if(motion.pose(1,2).x>.001) throw new AssertionError("Stopped zombie returns upright");
        try(var world=TestWorlds.disposable(context).create()) {
            var server=world.getServer();
            server.runCommand("gamemode spectator @a"); server.runCommand("time set midnight"); server.runCommand("effect give @a night_vision infinite 0 true");
            server.runCommand("fill -8 100 -8 8 100 16 stone");
            server.runCommand("tp @a 0 103 -8 0 10");
            server.runCommand("summon zombie 0 101 0 {Silent:1b}");
            server.runCommand("summon villager 0 101 12 {NoAI:1b,Silent:1b}");
            context.waitTicks(24);
            context.runOnClient(client -> {
                int count=0;
                for(var e:TestFixtures.level(client).entitiesForRendering()) if(e instanceof Zombie zombie) {
                    count++;
                    var state=TestFixtures.require((LivingEntityRenderState)client.getEntityRenderDispatcher().getRenderer(zombie).createRenderState(zombie,1), "(ZombieWalkState)state");
                    var lean=((ZombieWalkState)state).naturality$walkLean();
                    if(lean.x<.1 || lean.x>10 || Math.abs(lean.y)>1.8) throw new AssertionError("Walking zombie render lean: "+lean.x+","+lean.y+" ground="+zombie.onGround()+" velocity="+zombie.getDeltaMovement()+" position="+zombie.position()+" walk="+state.walkAnimationSpeed);
                }
                if(count!=1) throw new AssertionError("Zombie fixture missing");
            });
            Zombie[] blocked=new Zombie[1];
            server.runOnServer(s -> {
                for(var e:s.overworld().getAllEntities()) if(e instanceof Zombie zombie) blocked[0]=zombie;
                var zombie=blocked[0];zombie.setNoAi(true);
                int wallZ=(int)Math.floor(zombie.getZ())+1;
                int wallX=(int)Math.floor(zombie.getX());
                for(int dy=0;dy<5;dy++) s.overworld().setBlock(new net.minecraft.core.BlockPos(wallX,101+dy,wallZ),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                zombie.setPos(wallX+.5,101,wallZ-zombie.getBbWidth()/2-.001);
                zombie.needsSync=true;zombie.syncPosition=true;
            });
            for(int tick=0;tick<40;tick++) {
                server.runOnServer(_ -> {
                    var zombie=blocked[0];
                    zombie.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(0,.025,.3));
                    zombie.setDeltaMovement(0,.2,.3);zombie.needsSync=true;zombie.syncPosition=true;
                });context.waitTicks(1);
            }
            context.runOnClient(client -> {
                for(var e:TestFixtures.level(client).entitiesForRendering()) if(e instanceof Zombie zombie) {
                    var state=TestFixtures.require((LivingEntityRenderState)client.getEntityRenderDispatcher().getRenderer(zombie).createRenderState(zombie,1), "(ZombieWalkState)state");
                    var lean=((ZombieWalkState)state).naturality$walkLean();
                    if(lean.x>.1 || Math.abs(lean.y)>.1) throw new AssertionError("Blocked jumping zombie must ease upright despite attempted movement");
                }
            });
        }
    }
}



