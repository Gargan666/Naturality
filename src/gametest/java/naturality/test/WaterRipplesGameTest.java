package naturality.test;

import naturality.client.fluid.*;
import naturality.config.NaturalityConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;

public final class WaterRipplesGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        boolean[] saved=new boolean[3];
        context.runOnClient(c->{
            saved[0]=NaturalityConfig.get().liquids.water; saved[1]=c.options.improvedTransparency().get(); saved[2]=c.gui.hud.isHidden();
            NaturalityConfig.get().liquids.water=true;
            if(!saved[2])c.gui.hud.toggle();
        });
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set 6000");
            server.runOnServer(s -> s.setWeatherParameters(12000, 0, false, false));
            server.runCommand("tp @a 0.5 104 -7.5 0 40");
            server.runCommand("fill -16 99 -16 16 99 16 stone");
            server.runCommand("fill -16 100 -16 16 100 16 water");
            server.runCommand("fill -5 100 -5 5 100 -3 stone");
            for(int i=1;i<=3;i++)server.runCommand("setblock "+((i-2)*3)+" 101 -4 water_cauldron[level="+i+"]");
            server.runCommand("setblock 5 101 -4 cauldron");
            server.runCommand("setblock 6 101 -4 lava_cauldron");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(30);
            world.getConnection().waitForChunksRender();
            context.runOnClient(c->{
                for(int i=1;i<=3;i++) {
                    var p=new BlockPos((i-2)*3,101,-4); var s=WaterSurface.at(c.level,p);
                    check(s!=null && s.cauldron() && Math.abs(s.height(.5,.5)-(101+(6+3*i)/16.0))<1e-8,"Cauldron water height "+i);
                    check(!s.contains(.0625,.5) && s.contains(.5,.5),"Cauldron rim clipping");
                    int before=WaterRipples.activeCount();
                    var drop=c.particleEngine.createParticle(ParticleTypes.FALLING_WATER,p.getX()+.5,103,p.getZ()+.5,0,0,0);
                    for(int j=0;j<40 && drop.isAlive();j++)drop.tick();
                    check(WaterRipples.activeCount()==before+1,"Falling water must hit cauldron level "+i);
                }
                check(WaterSurface.at(c.level,new BlockPos(5,101,-4))==null,"Empty cauldron excluded");
                check(WaterSurface.at(c.level,new BlockPos(6,101,-4))==null,"Lava cauldron excluded");
                for(var type:new net.minecraft.core.particles.SimpleParticleType[]{ParticleTypes.FALLING_WATER,ParticleTypes.FALLING_DRIPSTONE_WATER,ParticleTypes.RAIN}) {
                    int before=WaterRipples.activeCount();
                    var drop=c.particleEngine.createParticle(type,.5,102,.5,0,0,0);
                    // Keep short-lived rain drops alive long enough for this deterministic impact test.
                    drop.setLifetime(100);
                    for(int j=0;j<50 && drop.isAlive();j++)drop.tick();
                    check(WaterRipples.activeCount()==before+1,"Water particle impact: "+type);
                }
                int before=WaterRipples.activeCount();
                var lava=c.particleEngine.createParticle(ParticleTypes.FALLING_LAVA,.5,102,.5,0,0,0);
                for(int j=0;j<30 && lava.isAlive();j++)lava.tick();
                check(WaterRipples.activeCount()==before,"Lava drip must not create water rings");
            });
            context.waitTicks(1);
            context.takeScreenshot("water-ripples-cauldron-drips-start");
            context.waitTicks(3);
            context.takeScreenshot("water-ripples-cauldron-drips");
            context.waitTicks(5);
            context.runOnClient(c->check(WaterRipples.activeCount()==0,"Rings must expire"));
            for(boolean oit:new boolean[]{false,true}) {
                context.runOnClient(c->c.options.improvedTransparency().set(oit));
                server.runCommand("summon pig 0.5 101.6 -3.5 {Age:-24000,NoAI:1b,NoGravity:1b,Tags:[\"cauldron_rim\"]}");
                world.getConnection().waitForClientboundPackets();context.waitTicks(8);
                context.runOnClient(c->{
                    check(WaterIntersection.lastPixelCount>0,"Entity rim on cauldron, OIT="+oit);
                    for(int i=1;i<=3;i++) WaterRipples.impact(c.level,(i-2)*3+.5,102,-3.5,(i-2)*3+.5,101,-3.5);
                });
                context.waitTicks(4);
                context.takeScreenshot("water-ripples-cauldron-model-"+oit);
                server.runCommand("kill @e[tag=cauldron_rim]");
            }
            // Roof covers half the pool. Rain must not make rings below it.
            server.runCommand("fill -16 105 0 -1 105 16 stone");
            server.runCommand("fillbiome -16 96 -16 16 112 16 plains");
            server.runCommand("tp @a 0.5 104 -1.5 0 40");
            server.runOnServer(s -> s.setWeatherParameters(0, 12000, true, false));
            world.getConnection().waitForClientboundPackets();context.waitTicks(120);
            context.runOnClient(c->{
                check(WaterRipples.activeRainCount()>0,"Sky-exposed rainy water must have rings");
                for(int x=-16;x<0;x++)for(int z=0;z<=16;z++)
                    check(WaterRipples.activeRainAt(new BlockPos(x,100,z))==0,"Sampled rain ring beneath roof");
                int before=WaterRipples.activeCount();
                var drip=c.particleEngine.createParticle(ParticleTypes.FALLING_DRIPSTONE_WATER,-4.5,103,4.5,0,0,0);
                for(int i=0;i<40 && drip.isAlive();i++)drip.tick();
                check(WaterRipples.activeCount()==before+1,"Real drips must still splash on sheltered water");
            });
            context.takeScreenshot("water-ripples-rain-and-roof");
            server.runOnServer(s -> s.setWeatherParameters(12000, 0, false, false));
            world.getConnection().waitForClientboundPackets();context.waitTicks(120);
            context.runOnClient(c->{
                check(WaterRipples.activeCount()==0,"Rain rings must stop after weather clears");
                NaturalityConfig.get().liquids.water=false;
                check(!WaterRipples.impact(c.level,.5,102,.5,.5,100,.5),"Disabled water must reject impacts");
            });
        } finally {
            context.runOnClient(c->{NaturalityConfig.get().liquids.water=saved[0];c.options.improvedTransparency().set(saved[1]);if(c.gui.hud.isHidden()!=saved[2])c.gui.hud.toggle();});
        }
    }
    private static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
}
