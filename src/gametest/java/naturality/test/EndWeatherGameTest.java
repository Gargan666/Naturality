package naturality.test;

import naturality.weather.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.Pose;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class EndWeatherGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    private static void cycleChecks() {
        var cycle=new EndWeatherCycle(123);
        check(cycle.state().equals(EndWeatherState.CLEAR),"New End weather starts upright and calm");
        cycle.set("gravity",100); for(int i=0;i<400;i++)cycle.tick(false);
        check(Math.abs(cycle.state().verticalGravity()-.1)<1e-6,"Gravity 100 weakens gravity by ninety percent");
        var legacy = new java.util.HashMap<>(cycle.snapshot());
        legacy.put("distortionOverride",1L);legacy.put("flipTarget",EnvironmentWorldData.bits(1));
        var copy=new EndWeatherCycle(9);copy.restore(legacy);
        check(copy.state().verticalGravity()>0 && !copy.snapshot().containsKey("distortionOverride"),"Legacy distortion weather is discarded");
        for(int i=0;i<3000;i++) {cycle.tick(true);copy.tick(true);check(cycle.snapshot().equals(copy.snapshot()),"Persisted End weather resumes exactly");}
    }
    @Override public void runTest(ClientGameTestContext context) {
        cycleChecks();
        net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave save;
        var saved=new java.util.HashMap<String,Long>();
        java.util.UUID[] endermanId={null};
        boolean[] naturalSpawnAllowed={false};
        try(var world=context.worldBuilder().create()) {
            save=world.getWorldSave();
            var server=world.getServer();
            server.runOnServer(s -> s.getLevel(Level.END).setDragonFight(null));
            server.runCommand("gamerule minecraft:advance_weather false");
            server.runCommand("gamemode spectator @a");
            server.runCommand("execute in minecraft:the_end run tp @a 0 100 0 0 0");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(60);
            server.runCommand("execute in minecraft:the_end run fill -12 98 -12 12 98 20 end_stone");
            server.runCommand("execute in minecraft:the_end run fill -12 105 -12 12 105 20 end_stone");
            server.runCommand("execute in minecraft:the_end run tp @a 0 99 0 0 0");
            server.runCommand("gamemode survival @a");
            server.runCommand("weather minecraft:the_end gravity 100");
            context.waitTicks(410);
            server.runOnServer(s -> {
                var end=s.getLevel(Level.END);
                var cow=EntityTypes.COW.create(end,EntitySpawnReason.COMMAND);
                cow.setNoAi(true);cow.setPos(3,99,3);end.addFreshEntity(cow);
                check(Math.abs(cow.getGravity()-.008)<1e-6,"Mobs receive reduced End gravity");
                var enderman=EntityTypes.ENDERMAN.create(end,EntitySpawnReason.COMMAND);
                enderman.setNoAi(true);enderman.setPersistenceRequired();enderman.setPos(6,106,6);end.addFreshEntity(enderman);endermanId[0]=enderman.getUUID();
                var normal=EntityTypes.COW.create(s.overworld(),EntitySpawnReason.COMMAND);
                check(Math.abs(normal.getGravity()-.08)<1e-6,"Other dimensions retain normal gravity");
                check(SpawnPlacements.checkSpawnRules(EntityTypes.ENDERMAN,end,EntitySpawnReason.TRIAL_SPAWNER,
                    new BlockPos(8,106,8),end.getRandom()),"Ordinary End gravity permits mob spawning");
                naturalSpawnAllowed[0]=SpawnPlacements.checkSpawnRules(EntityTypes.ENDERMAN,end,EntitySpawnReason.NATURAL,
                    new BlockPos(8,106,8),net.minecraft.util.RandomSource.create(42));
            });
            server.runCommand("execute in minecraft:the_end run effect give @e naturality:distortion infinite 0 true");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(5);
            server.runOnServer(s -> {
                var end=s.getLevel(Level.END);var enderman=end.getEntity(endermanId[0]);
                check(enderman!=null && Math.abs(enderman.getBoundingBox().maxY-105)<.05,
                    "Enderman seeks the underside within five ticks of the flip starting");
                check(!EndGravity.inverted(enderman),"Early escape precedes the gravity midpoint");
            });
            context.waitTicks(25);
            server.runOnServer(s -> check(Math.abs(s.getLevel(Level.END).getEntity(endermanId[0]).getBoundingBox().maxY-105)<.05,
                "Enderman keeps its footing while gravity is still turning"));
            context.waitTicks(120);
            server.runOnServer(s -> {
                var end=s.getLevel(Level.END);var p=end.players().getFirst();
                check(EndGravity.inverted(p),"The affected player is inverted");
                var enderman=end.getEntity(endermanId[0]);
                check(enderman!=null && Math.abs(enderman.getBoundingBox().maxY-105)<.05,
                    "Enderman teleports from the island top to its underside on a flip");
                check(EndGravity.softenUpwardMotion(p,-1)==-1,"Soft ceiling never changes downward falling");
check(p.onGround() && Math.abs(p.getBoundingBox().maxY-105)<.05,"Player stands on the ceiling without clipping: "+p.position()+" grounded="+p.onGround());
                var cow=EntityTypes.COW.create(end,EntitySpawnReason.COMMAND);
                DistortionFixtures.invert(cow); cow.setPos(4,103,4);cow.setDeltaMovement(0,1,0);cow.move(MoverType.SELF,new Vec3(0,2,0));
                check(cow.onGround() && Math.abs(cow.getBoundingBox().maxY-105)<.001,"Mobs recognize ceiling contact as ground");
                cow.jumpFromGround();check(cow.getDeltaMovement().y<0,"Jumping pushes away from the ceiling");
                check(p.getEyeY()<p.getY()+.5,"Upside-down eyes are at the bottom of the collision box");
                check(SpawnPlacements.checkSpawnRules(EntityTypes.ENDERMAN,end,EntitySpawnReason.NATURAL,
                    new BlockPos(8,106,8),net.minecraft.util.RandomSource.create(42))==naturalSpawnAllowed[0],"Individual distortion preserves ordinary natural spawn rules");
                check(SpawnPlacements.checkSpawnRules(EntityTypes.ENDERMAN,end,EntitySpawnReason.TRIAL_SPAWNER,
                    new BlockPos(8,106,8),end.getRandom()),"Individual distortion permits normal spawner rules");
                check(SpawnPlacements.checkSpawnRules(EntityTypes.ENDERMAN,s.overworld(),EntitySpawnReason.TRIAL_SPAWNER,
                    new BlockPos(8,106,8),end.getRandom()),"End distortion does not suppress Overworld spawning");
                // The game-test resource pack alone marks pigs as exempt.
                end.setBlockAndUpdate(new BlockPos(8,105,10),Blocks.GRASS_BLOCK.defaultBlockState());
                check(SpawnPlacements.checkSpawnRules(EntityTypes.PIG,end,EntitySpawnReason.TRIAL_SPAWNER,
                    new BlockPos(8,106,10),end.getRandom()),"Tagged mobs keep normal spawn eligibility");
                var immune=EntityTypes.PIG.create(end,EntitySpawnReason.COMMAND);
                check(!EndGravity.affected(immune) && Math.abs(immune.getGravity()-.08)<1e-6,
                    "Tagged mobs retain ordinary gravity strength and direction");
            });
            context.runOnClient(c -> {
                check(c.level.dimension().equals(Level.END),"Client is in the End");
                check(c.gameRenderer.mainCamera().rotation().transform(new org.joml.Vector3f(0,1,0)).y<-.99,
                    "Camera is smoothly rotated upside down");
                var p=c.particleEngine.createParticle(new net.minecraft.core.particles.BlockParticleOption(
                    net.minecraft.core.particles.ParticleTypes.BLOCK,net.minecraft.world.level.block.Blocks.END_STONE.defaultBlockState()),4,101,4,0,0,0);
                try {
                    var yd=net.minecraft.client.particle.Particle.class.getDeclaredField("yd");yd.setAccessible(true);yd.setDouble(p,0);
                    p.tick();check(yd.getDouble(p)<0,"Unowned particles retain downward gravity despite an affected player");p.remove();
                } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            context.runOnClient(EndFootstepParticleChecks::inverted);
            context.takeScreenshot("end-weather-inverted");
            context.runOnClient(c -> c.options.keyShift.setDown(true));
            context.waitTicks(12);
            context.runOnClient(c -> {
                check(c.player.getPose()==Pose.CROUCHING,"Crouch remains available on a ceiling");
                check(Math.abs(c.player.getBoundingBox().maxY-105)<.01,"Crouching keeps the feet anchored at the ceiling");
                check(c.level.noCollision(c.player,c.player.getBoundingBox().deflate(1.0e-7)),"Crouching does not clip into the ceiling");
            });
            context.runOnClient(c -> c.options.keyShift.setDown(false));
            context.waitTicks(12);
            context.runOnClient(c -> check(c.player.getPose()==net.minecraft.world.entity.Pose.STANDING,
                "Releasing crouch on a ceiling restores standing instead of forcing crawling: "+c.player.getPose()));
            server.runOnServer(s -> check(s.getLevel(Level.END).players().getFirst().getPose()==net.minecraft.world.entity.Pose.STANDING,
                "Server also restores the standing ceiling pose"));
            context.runOnClient(c -> c.player.setPose(Pose.SWIMMING));
            context.waitTicks(12);
            context.runOnClient(c -> check(c.player.getPose()==Pose.STANDING && Math.abs(c.player.getBoundingBox().maxY-105)<.01,
                "An already crawling player can recover to standing below a clear ceiling"));
            double[] startZ={0};
            context.runOnClient(c -> { startZ[0]=c.player.getZ();c.options.keyUp.setDown(true); });
            context.waitTicks(12);
            context.runOnClient(c -> {
                c.options.keyUp.setDown(false);
                check(c.player.getZ()>startZ[0]+.1 && c.player.onGround(),"Player can walk while attached to a ceiling");
                c.options.keyJump.setDown(true);
            });
            context.waitTicks(3);
            context.runOnClient(c -> {
                c.options.keyJump.setDown(false);
                check(c.player.getDeltaMovement().y<0,"Player jump control moves away from the ceiling");
            });
            context.waitTicks(60);
            server.runOnServer(s -> {
                var p=s.getLevel(Level.END).players().getFirst();
                p.teleportTo(p.getX(),EndGravity.upwardLimit(p)-5,p.getZ());
            });
            world.getConnection().waitForClientboundPackets();
            context.runOnClient(c -> c.player.setDeltaMovement(0,1,0));
            context.waitTicks(100);
            context.runOnClient(c -> {
                double top=EndGravity.upwardLimit(c.player);
                check(c.player.getY()<top && c.player.getY()>top-5,"Upward spring holds the player below its ceiling");
                check(c.player.getDeltaMovement().y<.1,"Upward fall slows near the soft limit");
            });
            server.runCommand("execute in minecraft:the_end run tp @a 0 102 0 0 0");
            server.runCommand("execute in minecraft:the_end run effect clear @e naturality:distortion");
            context.waitTicks(140);
            server.runOnServer(s -> {
                var p=s.getLevel(Level.END).players().getFirst();
                var enderman=s.getLevel(Level.END).getEntity(endermanId[0]);
                check(enderman!=null && enderman.getY()>105,"Enderman returns to island top when normal gravity resumes: "+(enderman==null?"missing":enderman.position()));
                check(!EndGravity.inverted(p) && p.onGround() && Math.abs(p.getY()-99)<.05,"Distortion end returns player safely to ordinary ground");
                check(SpawnPlacements.checkSpawnRules(EntityTypes.ENDERMAN,s.getLevel(Level.END),EntitySpawnReason.TRIAL_SPAWNER,
                    new BlockPos(8,106,8),s.getLevel(Level.END).getRandom()),"Normal spawning resumes after distortion ends");
            });
            context.runOnClient(c -> check(c.gameRenderer.mainCamera().rotation().transform(new org.joml.Vector3f(0,1,0)).y>.99,
                "Camera returns upright when distortion ends"));
            context.runOnClient(EndFootstepParticleChecks::upright);
            server.runOnServer(s -> saved.putAll(EndWeatherSystem.cycle(s.getLevel(Level.END)).snapshot()));
        }
        try(var other=context.worldBuilder().create()) {
            other.getServer().runOnServer(s -> check(EndWeatherSystem.state(s.getLevel(Level.END)).equals(EndWeatherState.CLEAR),
                "Another world does not inherit End weather"));
        }
        try(var reopened=save.open()) {
            reopened.getServer().runOnServer(s -> check(EndWeatherSystem.cycle(s.getLevel(Level.END)).snapshot().equals(saved),
                "End gravity, overrides and event clocks survive real world reopen"));
        }
    }
}
