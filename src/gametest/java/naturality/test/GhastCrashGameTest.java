package naturality.test;

import naturality.ghast.*;
import naturality.client.entity.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class GhastCrashGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    private static java.util.List<ItemEntity> diamonds(net.minecraft.server.level.ServerLevel level) {
        var result = new java.util.ArrayList<ItemEntity>();
        for (var e : level.getAllEntities()) if (e instanceof ItemEntity item && item.getItem().is(Items.DIAMOND)) result.add(item);
        return result;
    }
    @Override public void runTest(ClientGameTestContext context) {
        var pendulum=new GhastTentaclePhysics.Pendulum(new Vec3(.5,-.8,.2));
        for(int tick=0;tick<160;tick++) pendulum.tick(new Vec3(0,-.04,0),2);
        check(pendulum.direction.y<-.99,"Death tentacles settle under gravity instead of looping animation");
        for(int tick=0;tick<80;tick++) pendulum.tick(new Vec3(0,.04,-.08),2);
        check(pendulum.direction.z<-.8 && Math.abs(pendulum.direction.length()-1)<.00001,"Tentacles trail airflow while preserving length");
        check(GhastCrash.tipping(.25)==0 && GhastCrash.tipping(.7)>.02 && GhastCrash.tipping(1.65)==1,"Head-first orientation requires high speed");
        var simulation=new GhastCrash.Flight();
        simulation.impulse=new Vec3(0,0,.55); simulation.velocity=simulation.impulse;
        simulation.angularVelocity=new Vec3(.06,0,0);
        double lastSpeed=0;
        for(int age=1;age<=160;age++) {
            simulation.velocity=GhastCrash.integrateVelocity(simulation.velocity,age);
            check(-simulation.velocity.y>=lastSpeed-.000001 && simulation.velocity.length()<3.2,"Drag-limited continuous acceleration");
            lastSpeed=-simulation.velocity.y;
            Vec3 previous=simulation.rotation;
            GhastCrash.rotate(simulation);
            check(simulation.rotation.distanceTo(previous)<.16,"Bounded angular movement without rotation snaps");
        }
        check(lastSpeed>2.8 && lastSpeed<3.1,"Finite terminal fall speed");
        check(GhastMotion.expoInOut(0)==0 && GhastMotion.expoInOut(.5)==.5
            && GhastMotion.expoInOut(1)==1,"Rotation easing endpoints and midpoint");
        for (int i=0;i<=100;i++) {
            double t=i/100.0;
            check(Math.abs(GhastMotion.expoInOut(t)+GhastMotion.expoInOut(1-t)-1)<.000001,
                "Symmetric expo-in-out rotation");
        }
        check(GhastCrash.fallStretch(0)==1 && GhastCrash.fallStretch(-1)==1,"No death deformation without downward speed");
        check(GhastCrash.fallStretch(1)<GhastCrash.fallStretch(2)
            && GhastCrash.fallStretch(20)<=1.06001F,"Subtle speed-dependent deformation limit");
        try (var world = TestWorlds.disposable(context).create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("fill -48 90 -48 48 90 48 stone");
            server.runCommand("fill -14 91 -14 14 130 14 air");
            // An attractive elevated surface off the natural trajectory must not steer the fall.
            server.runCommand("setblock 3 100 8 stone");
            server.runCommand("tp @a 0 116 -13 0 0");
            server.runCommand("summon ghast 0.5 116 0.5 {NoAI:1b,NoGravity:1b,DeathLootTable:\"naturality:test_ghast_crash\"}");
            context.waitTicks(5);
            LivingEntity[] body = new LivingEntity[1];
            Vec3[] initial={Vec3.ZERO,Vec3.ZERO};
            server.runOnServer(s -> {
                var level = s.overworld();
                for (var e : level.getAllEntities()) if (e instanceof Ghast ghast) body[0]=ghast;
                body[0].hurtServer(level,level.damageSources().playerAttack(level.players().getFirst()),1000);
                var flight = TestFixtures.require(((GhastCrashBody)body[0]).naturality$flight(), "ghast flight");
                initial[0]=body[0].position(); initial[1]=flight.velocity;
                check(flight!=null && flight.drops.size()==1,"Normal loot held during crash");
                check(flight.impulse.z>0,"Recoil moves away from the killing hit");
                check(flight.angularVelocity.x>0,"Killing blow adds angular momentum in recoil direction");
                check(flight.experience>0,"Vanilla experience held until impact");
                body[0].spawnAtLocation(level,new net.minecraft.world.item.ItemStack(Items.LEAD));
                check(flight.drops.size()==2,"Leads shed during death also held");
                check(diamonds(level).isEmpty(),"No items spawned before impact");
                var saved = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,s.registryAccess());
                body[0].saveWithoutId(saved);
                Ghast restored = new Ghast(EntityTypes.GHAST,level);
                restored.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,s.registryAccess(),saved.buildResult()));
                var copy = ((GhastCrashBody)restored).naturality$flight();
                check(copy!=null && copy.drops.size()==2 && copy.impulse.equals(flight.impulse) && copy.velocity.equals(flight.velocity) && copy.angularVelocity.equals(flight.angularVelocity),"Crash and loot serialization");
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                for(var e:TestFixtures.level(client).entitiesForRendering()) if(e instanceof Ghast ghast) {
                    var state = TestFixtures.require((SquashingGhastRenderer.State)client.getEntityRenderDispatcher().getRenderer(ghast).createRenderState(ghast,1), "ghast render state");
                    check(ghast.hurtTime>0 && state.hasRedOverlay,"Killing blow retains the ordinary timed hit flash");
                }
            });
            context.waitTicks(6);

            server.runOnServer(_ -> {
                var flight = TestFixtures.require(((GhastCrashBody)body[0]).naturality$flight(), "ghast flight");
                Vec3 position=initial[0], velocity=initial[1];
                for(int age=1;age<=flight.age;age++) {
                    velocity=GhastCrash.integrateVelocity(velocity,age); position=position.add(velocity);
                }
                check(position.distanceTo(body[0].position())<.000001 && velocity.distanceTo(flight.velocity)<.000001,
                    "Nearby surfaces do not alter the freely integrated trajectory");
            });
            context.runOnClient(client -> {
                for (var e : TestFixtures.level(client).entitiesForRendering()) if (e instanceof Ghast ghast) {
                    var state = TestFixtures.require((SquashingGhastRenderer.State)client.getEntityRenderDispatcher().getRenderer(ghast).createRenderState(ghast,1), "ghast render state");
                    check(state.tentacles!=null && state.tentacles.pitch().length==9,"Death tentacle physics render state");
                    check(state.crash!=null && state.deathTime==0 && state.hasRedOverlay==(ghast.hurtTime>0),"Falling tint follows the normal hurt timer");
                }
            });
            context.takeScreenshot("ghast-death-stretch");
            context.waitTicks(14);
            server.runOnServer(s -> check(!body[0].isRemoved() && diamonds(s.overworld()).isEmpty(),"Crash exceeds vanilla death timer"));
            server.runCommand("tp @a 1 117 -12 0 0");
            context.waitTicks(2);
            context.runOnClient(client -> {
                int count=0;
                for (var e : TestFixtures.level(client).entitiesForRendering()) if (e instanceof Ghast ghast) {
                    count++;
                    var state = TestFixtures.require((SquashingGhastRenderer.State)client.getEntityRenderDispatcher().getRenderer(ghast).createRenderState(ghast,1), "ghast render state");
                    check(state.crash!=null && !state.hasRedOverlay,"Visible untinted fall beyond vanilla death duration");
                    check(state.crash.rotation().length()<2,"Moving body remains partly rotated");
                }
                check(count==1,"Client retains falling ghast beyond twenty ticks");
            });
            context.takeScreenshot("ghast-death-headfirst");
            server.runCommand("tp @a 3 94 1 0 20");
            boolean[] sawImpact = {false};
            for (int tick=0;tick<80;tick++) {
                context.waitTicks(1);
                context.runOnClient(client -> {
                    for (var e : TestFixtures.level(client).entitiesForRendering()) if (e instanceof Ghast ghast
                            && ((GhastCrashBody)ghast).naturality$impactTicks()>=0) {
                        var state = TestFixtures.require((SquashingGhastRenderer.State)client.getEntityRenderDispatcher().getRenderer(ghast).createRenderState(ghast,1), "ghast render state");
                        check(state.hasRedOverlay && state.deathTime==0,"Impact must turn red without native death roll");
                        check(((MobDeathState)(Object)state).naturality$deathPixels()>0,"Synced impact must enable pixel fade");
                        sawImpact[0]=true;
                    }
                });
                if (sawImpact[0] && tick<79) {
                    context.takeScreenshot("ghast-red-impact-fade");
                    context.waitTicks(8);
                    context.takeScreenshot("ghast-model-smoke-burst");
                    context.waitTicks(Math.max(1,71-tick));
                    break;
                }
            }
            server.runOnServer(_ -> {
                var flight = TestFixtures.require(((GhastCrashBody)body[0]).naturality$flight(), "ghast flight");
                check(sawImpact[0],"Client must observe synchronized impact phase: age="+flight.age+", impact="+flight.impactAge
                    +", synced="+((GhastCrashBody)body[0]).naturality$impactTicks()+", removed="+body[0].isRemoved());
            });
            context.takeScreenshot("ghast-vanilla-death-poof");
            context.waitTicks(18);

            server.runOnServer(s -> {
                var drops=diamonds(s.overworld());
                check(body[0].isRemoved() && drops.size()==1,"Exactly one loot drop at actual impact");
                var item=drops.getFirst();
                var landing=TestFixtures.require(((GhastCrashBody)body[0]).naturality$flight(), "ghast flight").landing;
                check(Math.abs(item.getX()-landing.x)<.01 && Math.abs(item.getZ()-landing.z)<.01
                    && item.getY()>=91 && item.getY()<91.3,"Loot remains at the natural impact point");
                check(item.getDeltaMovement().horizontalDistance()<.001,"No scattered item velocity");
            });
            server.runCommand("tp @a 3 94 1 0 20");
            context.waitTicks(2);
            context.takeScreenshot("ghast-natural-impact-drops");
            server.runCommand("kill @e[type=item]");
            server.runCommand("summon happy_ghast 0.5 112 0.5 {NoAI:1b,NoGravity:1b,DeathLootTable:\"naturality:test_ghast_crash\"}");
            context.waitTicks(5);
            server.runCommand("tp @a 0 112 -12 0 0");
            server.runOnServer(s -> {
                for (var e : s.overworld().getAllEntities()) if (e instanceof HappyGhast ghast) body[0]=ghast;
                var happy=(HappyGhast)body[0];
                happy.setItemSlot(EquipmentSlot.BODY,new net.minecraft.world.item.ItemStack(Items.HARNESS.red()));
                happy.setDropChance(EquipmentSlot.BODY,1);
                happy.hurtServer(s.overworld(),s.overworld().damageSources().playerAttack(s.overworld().players().getFirst()),1000);
                var flight = TestFixtures.require(((GhastCrashBody)happy).naturality$flight(), "ghast flight");
                check(flight.drops.stream().anyMatch(item -> item.is(Items.HARNESS.red())),"Harness held with death loot");
            });
            server.runCommand("tp @a 0 112 -12 0 0");
            context.waitTicks(18);
            context.takeScreenshot("happy-ghast-death-headfirst");
            boolean[] happyImpact={false};
            for(int tick=0;tick<60;tick++) {
                context.waitTicks(1);
                context.runOnClient(client -> {
                    for(var e:TestFixtures.level(client).entitiesForRendering()) if(e instanceof HappyGhast ghast
                            && ((GhastCrashBody)ghast).naturality$impactTicks()>=0) {
                        var state = TestFixtures.require((LeaningHappyGhastRenderer.State)client.getEntityRenderDispatcher().getRenderer(ghast).createRenderState(ghast,1), "ghast render state");
                        check(state.hasRedOverlay && ((MobDeathState)(Object)state).naturality$deathPixels()>0,"Happy ghast impact tint and fade");
                        happyImpact[0]=true;
                    }
                });
            }
            check(happyImpact[0],"Happy ghast impact reaches client");
            server.runOnServer(s -> {
                check(body[0].isRemoved() && diamonds(s.overworld()).size()==1,"Happy ghast natural ground impact");
                int harness=0;
                for (var e : s.overworld().getAllEntities()) if (e instanceof ItemEntity item && item.getItem().is(Items.HARNESS.red())) {
                    harness++; check(item.getY()>=91 && item.getY()<91.3,"Harness on actual impact surface");
                }
                check(harness==1,"Exactly one harness released on impact");
            });
            server.runCommand("kill @e[type=item]");
            server.runCommand("tp @a 0 180 -12 0 0");
            server.runCommand("summon ghast 0.5 180 0.5 {NoAI:1b,NoGravity:1b,DeathLootTable:\"naturality:test_ghast_crash\"}");
            context.waitTicks(5);
            server.runOnServer(s -> {
                for(var e:s.overworld().getAllEntities()) if(e instanceof Ghast ghast) body[0]=ghast;
                body[0].hurtServer(s.overworld(),s.overworld().damageSources().playerAttack(s.overworld().players().getFirst()),1000);
            });
            context.waitTicks(25);
            server.runOnServer(_ -> {
                var flight = TestFixtures.require(((GhastCrashBody)body[0]).naturality$flight(), "ghast flight");
                check(!body[0].isRemoved() && flight.rotation.length()<1,"Slow start of long fall remains largely upright");
            });
            context.waitTicks(40);
            server.runOnServer(_ -> {
                var flight = TestFixtures.require(((GhastCrashBody)body[0]).naturality$flight(), "ghast flight");
                check(!body[0].isRemoved() && flight.velocity.y<-2 && flight.rotation.length()>1.5,"High-speed long fall progressively tips head-first");
            });
            server.runCommand("tp @a 3 115 -5 0 15");
            context.waitTicks(2);
            context.takeScreenshot("ghast-fast-physical-fall");
            context.waitTicks(50);
            server.runOnServer(s -> check(body[0].isRemoved() && diamonds(s.overworld()).size()==1,"Long physical fall reaches ground without a destination"));
            server.runCommand("kill @e[type=item]");
            server.runCommand("fill -48 90 -48 48 90 48 lava");
            server.runCommand("setblock 3 100 8 air");
            server.runCommand("summon happy_ghast 0.5 116 0.5 {NoAI:1b,NoGravity:1b,DeathLootTable:\"naturality:test_ghast_crash\"}");
            context.waitTicks(5);
            server.runCommand("tp @a 0 112 -12 0 0");
            server.runOnServer(s -> {
                for (var e : s.overworld().getAllEntities()) if (e instanceof HappyGhast ghast) body[0]=ghast;
                body[0].hurtServer(s.overworld(),s.overworld().damageSources().playerAttack(s.overworld().players().getFirst()),1000);
                ((GhastCrashBody)body[0]).naturality$flight();
            });
            context.waitTicks(100);
            server.runOnServer(s -> check(body[0].isRemoved() && diamonds(s.overworld()).isEmpty(),"Natural impact in lava loses loot"));
            server.runCommand("summon happy_ghast 0.5 120 0.5 {NoAI:1b,NoGravity:1b}");
            context.waitTicks(5);
            HappyGhast[] mount=new HappyGhast[1];
            Vec3[] originalSeat=new Vec3[1];
            server.runOnServer(s -> {
                for (var e : s.overworld().getAllEntities()) if (e instanceof HappyGhast ghast) mount[0]=ghast;
                var player=s.overworld().players().getFirst();
                check(player.startRiding(mount[0],true,true),"Player mounts happy ghast");
                originalSeat[0]=mount[0].getPassengerRidingPosition(player).subtract(mount[0].position());
                for (int i=0;i<3;i++) {
                    var pig=new net.minecraft.world.entity.animal.pig.Pig(EntityTypes.PIG,s.overworld());
                    pig.setNoAi(true); s.overworld().addFreshEntity(pig); pig.startRiding(mount[0],true,true);
                }
                mount[0].setDeltaMovement(0,0,.2); mount[0].needsSync=true;
            });
            context.waitTicks(10);
            server.runOnServer(s -> {
                check(mount[0].getPassengers().size()==4,"All four ride attachments");
                var player=s.overworld().players().getFirst();
                Vec3 seat=mount[0].getPassengerRidingPosition(player).subtract(mount[0].position());
                check(seat.z>originalSeat[0].z+.03,"Front player seat follows forward lean");
                check(Math.abs(seat.y-originalSeat[0].y)>.001,"Seat height follows rotation");
            });
        }
    }
}

