package naturality.test;
import naturality.client.entity.FishSwim;
import naturality.client.entity.FishSwimState;
import naturality.fish.FishMoveControl;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
public final class FishSwimGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    public void runTest(ClientGameTestContext context) {
        check(Math.abs(FishMoveControl.swimSpeed(1.25,1,true)/FishMoveControl.swimSpeed(1.25,1,false)-2)<.000001,"Panic speed is twice the original panic rate");
        check(FishMoveControl.swimSpeed(1,1,false)==.42 && FishMoveControl.swimSpeed(10,1,true)==1.08,"Regular speed stays unchanged and panic has its own higher cap");
        var motion=new FishSwim.Motion(0,0,0);
        for(int i=0;i<40;i++) motion.tick(new Vec3(0,.04,.04));
        check(Math.abs(motion.sample(1).pitch()+45)<.1,"Fish pitches upward with diagonal swimming");
        float previous=motion.sample(1).phase();motion.tick(Vec3.ZERO);
        check(motion.sample(1).phase()>previous,"Idle animation slows instead of snapping to a stop");
        for(int i=0;i<100;i++) motion.tick(Vec3.ZERO);
        previous=motion.sample(1).phase();motion.tick(Vec3.ZERO);
        check(motion.sample(1).phase()==previous,"Idle swimming animation settles at a fixed phase");
        for(int i=0;i<40;i++) motion.tick(new Vec3(.04,-.04,0));
        check(Math.abs(motion.sample(1).pitch()-45)<.1,"Fish pitches down when descending");
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();server.runCommand("gamemode spectator @a");
            server.runCommand("fill -8 100 -8 8 112 8 water");
            server.runCommand("tp @a 0 106 -6 0 0");
            server.runCommand("effect give @a night_vision infinite 0 true");
            server.runCommand("summon cod 0 103 0 {NoAI:1b}");context.waitTicks(8);
            AbstractFish[] fish=new AbstractFish[1];Vec3[] start=new Vec3[1];
            server.runOnServer(s -> {
                for(var e:s.overworld().getAllEntities()) if(e instanceof AbstractFish f) fish[0]=f;
                start[0]=fish[0].position();
                check(fish[0].getMoveControl() instanceof FishMoveControl,"Fish uses direction-based movement controller");
                check(fish[0].getNavigation().moveTo(fish[0].getX(),fish[0].getY()+3,fish[0].getZ()+4,1),"Ascending swimming path exists");
            });
            for(int tick=0;tick<18;tick++) {
                server.runOnServer(_ -> {
                    fish[0].getNavigation().tick();fish[0].getMoveControl().tick();
                    fish[0].travel(Vec3.ZERO);fish[0].needsSync=true;fish[0].syncPosition=true;
                });context.waitTicks(1);
            }
            server.runOnServer(_ -> check(fish[0].getY()>start[0].y+.2 && fish[0].getZ()>start[0].z+.2 && fish[0].position().distanceTo(start[0])>1.5,"Fish actually travels toward an ascending waypoint"));
            context.runOnClient(client -> {
                for(var e:TestFixtures.level(client).entitiesForRendering()) if(e instanceof AbstractFish f) {
                    var state=TestFixtures.require((LivingEntityRenderState)client.getEntityRenderDispatcher().getRenderer(f).createRenderState(f,1), "(FishSwimState)state");
                    var pose=((FishSwimState)state).naturality$swim();
                    check(pose!=null && pose.pitch()<-5,"Rendered fish faces its upward movement");
                }
            });
            server.runCommand("tp @a -3 104 2 -90 0");context.waitTicks(2);
            context.takeScreenshot("fish-directional-swimming");
            server.runOnServer(_ -> {
                fish[0].getNavigation().stop();fish[0].getMoveControl().setWait();
                fish[0].setDeltaMovement(Vec3.ZERO);fish[0].needsSync=true;
            });
            server.runCommand("summon salmon -3 104 0 {NoAI:1b}");
            server.runCommand("summon tropical_fish 3 104 0 {NoAI:1b}");
            server.runCommand("summon pufferfish 0 104 4 {NoAI:1b}");
            context.waitTicks(110);
            var phases=new java.util.HashMap<Integer,Float>();
            context.runOnClient(client -> {
                for(var e:TestFixtures.level(client).entitiesForRendering()) if(e instanceof AbstractFish f) {
                    var state=TestFixtures.require((LivingEntityRenderState)client.getEntityRenderDispatcher().getRenderer(f).createRenderState(f,1), "(FishSwimState)state");
                    var pose=((FishSwimState)state).naturality$swim();
                    check(pose!=null && state.ageInTicks==pose.phase(),"All fish models use the eased animation phase");
                    phases.put(f.getId(),TestFixtures.require(pose, "pose").phase());
                }
                check(phases.size()==4,"All four vanilla fish renderers covered");
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                for(var e:TestFixtures.level(client).entitiesForRendering()) if(e instanceof AbstractFish f)
                    check(Math.abs(FishSwim.pose(f,1).phase()-phases.get(f.getId()))<.0001,"Stationary fish animation remains paused");
            });
            Vec3[] idleStart=new Vec3[1];float[] initialYaw=new float[1];double[] peakUp={0};
            server.runOnServer(_ -> {idleStart[0]=fish[0].position();initialYaw[0]=fish[0].getYRot();});
            for(int tick=0;tick<100;tick++) {
                server.runOnServer(_ -> {
                    fish[0].getMoveControl().tick();fish[0].travel(Vec3.ZERO);
                    fish[0].needsSync=true;fish[0].syncPosition=true;
                });context.waitTicks(1);
            }
            server.runOnServer(_ -> {
                double fall=idleStart[0].y-fish[0].getY();
                check(fall>.05 && fall<.4,"Idle fish sinks very slowly");
                check(Math.abs(net.minecraft.util.Mth.wrapDegrees(fish[0].getYRot()-initialYaw[0]))>.1,"Idle fish readjusts its looking direction");
                fish[0].setPos(fish[0].getX(),idleStart[0].y-.6,fish[0].getZ());
                idleStart[0]=fish[0].position();
            });
            for(int tick=0;tick<18;tick++) {
                server.runOnServer(_ -> {
                    fish[0].getMoveControl().tick();peakUp[0]=Math.max(peakUp[0],fish[0].getDeltaMovement().y);
                    fish[0].travel(Vec3.ZERO);fish[0].needsSync=true;fish[0].syncPosition=true;
                });context.waitTicks(1);
            }
            server.runOnServer(_ -> check(peakUp[0]>.04 && fish[0].getY()>idleStart[0].y+.2
                && fish[0].position().subtract(idleStart[0]).horizontalDistance()>.1,"Sunken idle fish makes an upward-forward recovery burst"));
            server.runOnServer(s -> {
                fish[0].setNoAi(false);
                fish[0].hurtServer(s.overworld(),s.overworld().damageSources().playerAttack(s.overworld().players().getFirst()),.5F);
            });context.waitTicks(5);
            server.runOnServer(_ -> check(fish[0].isPanicking() && fish[0].getDeltaMovement().length()>.31,"Hurt fish enters panic and exceeds regular swimming speed"));
        }
    }
}


