package naturality.test;

import naturality.client.fluid.*;
import naturality.config.NaturalityConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;

public final class LavaEffectsGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override public void runTest(ClientGameTestContext context) {
        boolean[] saved=new boolean[3];
        context.runOnClient(c->{saved[0]=NaturalityConfig.get().liquids.water;saved[1]=NaturalityConfig.get().liquids.lava;saved[2]=c.options.improvedTransparency().get();NaturalityConfig.get().liquids.water=false;NaturalityConfig.get().liquids.lava=true;});
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("gamemode spectator @a");server.runCommand("time set 6000");
            server.runCommand("tp @a 4 104 -5 35 25");
            server.runCommand("fill -8 99 -8 8 99 8 stone");server.runCommand("fill -8 100 -8 8 100 8 lava");
            server.runCommand("setblock 6 101 6 lava_cauldron");
            server.runCommand("setblock -5 100 -5 water");
            world.getConnection().waitForClientboundPackets();context.waitTicks(30);
            context.runOnClient(c->{
                check(LavaSurface.at(c.level,new BlockPos(0,100,0))!=null,"Lava surface recognized");
                check(WaterSurface.at(c.level,new BlockPos(0,100,0))==null,"Water effects must reject lava");
                check(LavaSurface.at(c.level,new BlockPos(6,101,6)).cauldron(),"Lava cauldron supported");
                check(LavaImpactColumn.heightFor(12)==WaterImpactColumn.heightFor(12)*.5,"Lava splash half height");
                check(LavaImpactColumn.RISE_TICKS==WaterImpactColumn.RISE_TICKS*2 && LavaImpactColumn.FALL_TICKS==WaterImpactColumn.FALL_TICKS*2,"Lava splash twice as slow");
                check(LavaRipples.impact(c.level,0,102,0,0,100.5,0),"Lava drip impact ring");
                check(LavaRipples.activeCount()>0,"Lava rings work with water disabled");
                var p=c.particleEngine.createParticle(naturality.NaturalityParticles.LAVA_SPLASH,0,103,0,0,.1,0);
                check(p instanceof naturality.client.particle.LavaSplashParticle,"Molten droplet provider registered");
                for(int i=0;i<100 && p.isAlive();i++)p.tick();
                check(!p.isAlive(),"Molten droplet terminates on contact");
                int rings=LavaRipples.activeCount();
                var pop=c.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.LAVA,1,101,1,0,0,0);
                pop.setParticleSpeed(0,-.15,0);
                for(int i=0;i<8 && pop.isAlive();i++)pop.tick();
                check(LavaRipples.activeCount()>rings,"Vanilla lava pop creates a ring when landing");
                int before=LavaRipples.activeCount();
                check(LiquidDropletContact.impact(c.level,false,2,102,2,2,100.5,2),"Water drop quenches on lava");
                check(LavaRipples.activeCount()==before,"Water drop into lava must not create a lava ring");
                check(LiquidDropletContact.impact(c.level,true,-5,102,-5,-5,100.5,-5),"Lava drop quenches on water even with water effects disabled");
                var drip=c.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.FALLING_LAVA,3,101.2,3,0,0,0);
                for(int i=0;i<15 && drip.isAlive();i++)drip.tick();
                check(LavaRipples.activeCount()>before,"Vanilla lava drips create lava rings");
            });
            for(boolean oit:new boolean[]{false,true}) {
                context.runOnClient(c->c.options.improvedTransparency().set(oit));
                server.runCommand("summon pig 0 108 0 {Invulnerable:1b,Tags:[\"lava_drop\"]}");
                world.getConnection().waitForClientboundPackets();
                int[] impacts={0};
                for(int i=0;i<60;i++) {
                    context.waitTicks(1);context.runOnClient(c->impacts[0]+=LavaEntrySplash.lastSplashCount);
                    if(impacts[0]>0){context.waitTicks(5);context.takeScreenshot("lava-impact-"+oit);break;}
                }
                check(impacts[0]==1,"High fall into lava creates one splash");
                server.runCommand("kill @e[tag=lava_drop]");context.waitTicks(70);
            }
            server.runCommand("summon pig 0 100.5 0 {NoAI:1b,NoGravity:1b,Invulnerable:1b,Tags:[\"lava_rim\"]}");
            server.runOnServer(s -> s.setWeatherParameters(0, 12000, true, false));context.waitTicks(100);
            context.runOnClient(c->{check(LavaIntersection.lastPixelCount>0,"Lava model rim rendered");check(LavaRipples.rainSmokeSpawnCount>0,"Sky exposed lava receives rain smoke");});
            context.takeScreenshot("lava-rim-rain");
            server.runCommand("kill @e[tag=lava_rim]");
            server.runOnServer(s -> s.setWeatherParameters(12000, 0, false, false));
            context.runOnClient(c->naturality.client.portal.FlatModelAlpha.lastCutPixels=0);
            server.runCommand("summon strider 0 100.2 0 {NoAI:1b,NoGravity:1b,Invulnerable:1b,Tags:[\"alpha_strider\"]}");
            world.getConnection().waitForClientboundPackets();context.waitTicks(12);
            context.runOnClient(c->check(naturality.client.portal.FlatModelAlpha.lastCutPixels>0,"Strider flat planes must use their texture alpha mask"));
            context.takeScreenshot("lava-strider-alpha-border");
            context.runOnClient(c->NaturalityConfig.get().liquids.lava=false);context.waitTicks(3);
            context.runOnClient(c->{check(LavaIntersection.lastPixelCount==0,"Lava toggle disables geometry");check(LavaRipples.activeCount()==0,"Lava toggle clears rings");});
        } finally {
            context.runOnClient(c->{NaturalityConfig.get().liquids.water=saved[0];NaturalityConfig.get().liquids.lava=saved[1];c.options.improvedTransparency().set(saved[2]);});
        }
    }
}



