package naturality.test;

import naturality.NaturalityParticles;
import naturality.client.fluid.WaterfallSplash;
import naturality.client.particle.WaterfallParticle;
import naturality.config.NaturalityConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ParticleStatus;

public final class WaterfallGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client->{
            var buffers=new net.minecraft.client.sounds.SoundBufferLibrary(client.getResourceManager());
            var original=buffers.getCompleteBuffer(naturality.Naturality.id("sounds/watersplash_loud.ogg")).join();
            var mono=buffers.getCompleteBuffer(naturality.Naturality.id("positional_sound/naturality/sounds/watersplash_loud.ogg")).join();
            check(mono.format().getChannels()==1,"Splash audio must be mono for positional attenuation");
            check(mono.size()*original.format().getChannels()==original.size(),"Downmix must preserve splash duration");
            buffers.clear();
        });
        check(naturality.client.fluid.FallParticleDensity.multiplier(36)==1,"Full spray through nearby distance");
        check(naturality.client.fluid.FallParticleDensity.multiplier(54)==0.5,"Half spray halfway through fade");
        check(naturality.client.fluid.FallParticleDensity.multiplier(72)==0,"No spray beyond extended distance");
        boolean[] saved=new boolean[4]; ParticleStatus[] particleStatus=new ParticleStatus[1];
        context.runOnClient(c->{
            var cfg=NaturalityConfig.get().liquids;
            saved[0]=cfg.water;saved[1]=cfg.waterfallParticles;saved[2]=c.options.improvedTransparency().get();saved[3]=c.gui.hud.isHidden();
            cfg.water=true;cfg.waterfallParticles=true;
            particleStatus[0]=c.options.particles().get();c.options.particles().set(ParticleStatus.ALL);
            if(!saved[3])c.gui.hud.toggle();
        });
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("gamemode spectator @a");server.runOnServer(s -> s.setWeatherParameters(12000, 0, false, false));server.runCommand("time set 6000");
            server.runCommand("tp @a 4.5 102.5 -5.5 35 12");
            server.runCommand("fill -9 99 -9 9 99 9 stone");
            server.runCommand("fill -9 100 -9 9 100 9 water");
            server.runCommand("fill -2 107 -1 2 107 1 stone");
            server.runCommand("fill -1 107 0 1 107 0 water");
            server.runCommand("fill -1 101 0 1 106 0 water[level=8]");
            world.getConnection().waitForClientboundPackets();context.waitTicks(45);
            world.getConnection().waitForChunksRender();
            context.runOnClient(c->WaterParticleTintChecks.check(c));
            server.runCommand("tp @a 4.5 179 -5.5 35 0");
            world.getConnection().waitForClientboundPackets();context.waitTicks(3);
            context.runOnClient(c->{

                var droplet=c.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.SPLASH,5,180,5,0,0,0);
                check(droplet instanceof naturality.client.particle.ImpactDroplet,"Impact spray must support landing lifetime");
                ((naturality.client.particle.ImpactDroplet)droplet).naturality$landBeforeExpiring();
                droplet.setParticleSpeed(0,0,0);
                for(int tick=0;tick<45;tick++)droplet.tick();
                check(droplet.isAlive(),"Impact droplet must outlive vanilla's maximum airborne lifetime");
                for(int tick=0;tick<120 && droplet.isAlive();tick++)droplet.tick();
                check(!droplet.isAlive(),"Impact droplet must terminate on water or ground contact");
            });
            server.runCommand("tp @a 4.5 102.5 -5.5 35 12");
            world.getConnection().waitForClientboundPackets();context.waitTicks(3);
            context.runOnClient(c->{
                check(naturality.client.fluid.FallImpactDroplets.vertical(c.level,new BlockPos(0,100,0),false),"Vertical water impact emits droplets");
                check(!naturality.client.fluid.FallImpactDroplets.vertical(c.level,new BlockPos(5,100,5),false),"Resting pool must not emit droplets");
                check(!naturality.client.fluid.FallImpactDroplets.vertical(c.level,new BlockPos(0,100,0),true),"Water fall must not emit lava droplets");
                check(WaterfallSplash.impactColumn(c.level,new BlockPos(0,100,0)),"Waterfall must enter a source pool");
                check(WaterfallSplash.validBorder(c.level,new WaterfallSplash.Border(new BlockPos(0,100,0),Direction.NORTH)),"Direct waterfall splashes at the adjacent source pool");
                check(!WaterfallSplash.validBorder(c.level,new WaterfallSplash.Border(new BlockPos(0,100,-1),Direction.NORTH)),"Source surface smoothing must not move spray one block outward");
                check(!WaterfallSplash.validBorder(c.level,new WaterfallSplash.Border(new BlockPos(0,100,0),Direction.EAST)),"No spray inside adjacent falling columns");
                check(!WaterfallSplash.impactColumn(c.level,new BlockPos(5,100,5)),"Resting water alone must not splash");
                check(!WaterfallSplash.impactColumn(c.level,new BlockPos(0,104,0)),"No spray halfway down a waterfall");
                var small=c.particleEngine.createParticle(NaturalityParticles.WATERFALL,0,101,0,0.02,0,0);
                var big=c.particleEngine.createParticle(NaturalityParticles.WATERFALL_BIG,0,101,0,0.02,0,0);
                check(small instanceof WaterfallParticle && big instanceof WaterfallParticle,"Both providers registered");
                check(((WaterfallParticle)big).getQuadSize(0)>1.8*((WaterfallParticle)small).getQuadSize(0),"Distinct large variant despite independent size variation");
                check(small.getLifetime()==24 && big.getLifetime()==24,"Twelve two-tick animation frames");
                double y=small.getBoundingBox().minY;
                for(int i=0;i<8;i++)small.tick();
                check(small.getBoundingBox().minY>y,"Splash must rise from the impact");
                for(int i=8;i<24;i++)small.tick();
                check(!small.isAlive(),"Splash must expire after its animation");
            });
            server.runCommand("summon pig 5 108 5 {Tags:[\"splash_test\"]}");
            world.getConnection().waitForClientboundPackets();
            check(naturality.client.fluid.WaterImpactColumn.heightFor(20)>naturality.client.fluid.WaterImpactColumn.heightFor(7),"Higher falls need taller columns");
            check(naturality.client.fluid.WaterImpactColumn.envelope(1.5)==1 && naturality.client.fluid.WaterImpactColumn.envelope(21.5)==0,"Column rises then fully collapses");
            check(naturality.client.fluid.WaterImpactColumn.spreadPixels(1.5)==0 && naturality.client.fluid.WaterImpactColumn.spreadPixels(11.5)==6 && naturality.client.fluid.WaterImpactColumn.spreadPixels(21.5)==12,"Footprint expands in whole pixels during collapse");
            check(naturality.client.fluid.WaterImpactColumn.heightPixels(2,1.5,1)==32 && naturality.client.fluid.WaterImpactColumn.heightPixels(2,21.5,1)==0,"Height uses complete one-sixteenth-block rows");
            check(naturality.client.fluid.WaterImpactColumn.spreadPixels(4)==1 && naturality.client.fluid.WaterImpactColumn.envelope(4)>.98,"Width advances linearly independently while height eases in");
            check(Math.abs(naturality.client.fluid.WaterImpactColumn.splashChannel(64,.5F,0)-64/255F*.5F)<.0001,"Splash base must preserve dark biome water palette");
            check(Math.abs(naturality.client.fluid.WaterImpactColumn.splashChannel(64,.5F,1)-(64/255F*1.25F+.22F))<.0001,"Splash tip must match rain-ring palette");
            for(double riseAge:new double[]{0,.25,.75,1.49,1.5}) {
                check(naturality.client.fluid.WaterImpactColumn.spreadPixels(riseAge)==0,"No widening before rise completes");
            }
            check(naturality.client.fluid.WaterImpactColumn.envelope(.125)<naturality.client.fluid.WaterImpactColumn.envelope(1.5),"Rise reaches its peak before falling begins");
            check(naturality.client.fluid.WaterImpactColumn.ringSpreadPixels(26.5)>naturality.client.fluid.WaterImpactColumn.ringSpreadPixels(21.5),"Ring continues outward after column collapses");
            check(naturality.client.fluid.WaterImpactColumn.ringFade(21.5)==1 && naturality.client.fluid.WaterImpactColumn.ringFade(26.5)==.5F && naturality.client.fluid.WaterImpactColumn.ringFade(31.5)==0,"Residual ring fades continuously to zero");
            int[] loud=new int[1];
            for(int i=0;i<50;i++) {
                context.waitTicks(1);
                context.runOnClient(c->loud[0]+=naturality.client.fluid.WaterEntrySplash.lastSplashCount);
                if(loud[0]==1 && i<30) {
                    context.waitTicks(4);
                    context.takeScreenshot("water-impact-column");
                    i=30;
                }
            }
            check(loud[0]==1,"A high fall into water must produce exactly one loud splash");
            server.runCommand("kill @e[tag=splash_test]");
            server.runCommand("summon pig 5 104.5 5 {Tags:[\"splash_test\"]}");
            world.getConnection().waitForClientboundPackets();
            loud[0]=0;
            for(int i=0;i<35;i++) {
                context.waitTicks(1);
                context.runOnClient(c->loud[0]+=naturality.client.fluid.WaterEntrySplash.lastSplashCount);
                if(loud[0]==1 && i<30) {
                    context.waitTicks(4);
                    context.takeScreenshot("water-impact-column");
                    i=30;
                }
            }
            check(loud[0]==0,"A short fall into water must not produce a loud splash");
            server.runCommand("kill @e[tag=splash_test]");
            server.runCommand("summon oak_boat 5 109 5 {Tags:[\"boat_splash_test\"]}");
            world.getConnection().waitForClientboundPackets();
            loud[0]=0;
            for(int i=0;i<60;i++) {
                context.waitTicks(1);
                context.runOnClient(c->loud[0]+=naturality.client.fluid.WaterEntrySplash.lastSplashCount);
            }
            check(loud[0]==1,"A boat falling into water must produce one large splash");
            server.runCommand("kill @e[tag=boat_splash_test]");
            int[] counts=new int[2];
            for(int i=0;i<50;i++) {
                context.waitTicks(1);
                context.runOnClient(c->{counts[0]+=WaterfallSplash.lastSmallSpawns;counts[1]+=WaterfallSplash.lastBigSpawns;});
            }
            check(counts[0]>0 && counts[1]>0,"Waterfall must automatically emit both variants");
            for(boolean oit:new boolean[]{false,true}) {
                context.runOnClient(c->c.options.improvedTransparency().set(oit));context.waitTicks(15);
                context.takeScreenshot("waterfall-splash-"+oit);
            }
            // Replace the pool with a floor: the falling foot now spreads into a
            // shallow, non-source sloping skirt like a waterfall on a shore ledge.
            server.runCommand("fill -9 100 -9 9 100 9 stone");
            world.getConnection().waitForClientboundPackets();context.waitTicks(70);
            world.getConnection().waitForChunksRender();
            context.runOnClient(c->WaterParticleTintChecks.check(c));
            server.runCommand("tp @a 4.5 179 -5.5 35 0");
            world.getConnection().waitForClientboundPackets();context.waitTicks(3);
            context.runOnClient(c->{
                var foot=new BlockPos(0,101,0);
                check(!WaterfallSplash.impactColumn(c.level,foot),"Supported falling foot without resting water must not emit");
                check(!c.level.getFluidState(foot.north()).isSource(),"Regression scene must contain flowing runoff");
                check(!WaterfallSplash.validBorder(c.level,new WaterfallSplash.Border(foot,Direction.NORTH)),"Flowing-to-sloping transition must not emit");
                check(!WaterfallSplash.impactColumn(c.level,new BlockPos(0,104,0)),"Slope support must not enable midair spray");
                check(!WaterfallSplash.validBorder(c.level,new WaterfallSplash.Border(foot.north(),Direction.NORTH)),"Runoff-to-runoff transition must not emit");
            });
            counts[0]=counts[1]=0;
            for(int i=0;i<40;i++) {
                context.waitTicks(1);
                context.runOnClient(c->{counts[0]+=WaterfallSplash.lastSmallSpawns;counts[1]+=WaterfallSplash.lastBigSpawns;});
            }
            check(counts[0]==0 && counts[1]==0,"Runoff without resting water must not emit either variant");
            // Restore the pool: its first row is source water with surface smoothing; the
            // impact remains adjacent to the falling column rather than one row farther out.
            server.runCommand("fill -9 100 -9 9 100 9 water");
            server.runCommand("fill -9 101 -9 9 101 9 air");
            server.runCommand("fill -1 101 0 1 106 0 water[level=8]");
            world.getConnection().waitForClientboundPackets();context.waitTicks(45);
            for(boolean oit:new boolean[]{false,true}) {
                context.runOnClient(c->c.options.improvedTransparency().set(oit));context.waitTicks(15);
                context.takeScreenshot("waterfall-sloped-runoff-"+oit);
            }
            context.runOnClient(c->NaturalityConfig.get().liquids.waterfallParticles=false);context.waitTicks(2);
            context.runOnClient(c->check(WaterfallSplash.lastSmallSpawns+WaterfallSplash.lastBigSpawns==0,"Toggle stops spawning"));
            context.runOnClient(c->{NaturalityConfig.get().liquids.waterfallParticles=true;c.options.particles().set(ParticleStatus.MINIMAL);});
            context.waitTicks(2);
            context.runOnClient(c->check(WaterfallSplash.lastSmallSpawns+WaterfallSplash.lastBigSpawns==0,"Minimal particles respected"));
            context.runOnClient(c->c.options.particles().set(ParticleStatus.ALL));
            server.runCommand("fill -1 101 0 1 107 0 stone");
            world.getConnection().waitForClientboundPackets();context.waitTicks(30);
            context.runOnClient(c->check(WaterfallSplash.lastSmallSpawns+WaterfallSplash.lastBigSpawns==0,"Removed waterfall must stop spawning"));
            context.takeScreenshot("waterfall-removed");
        } finally {
            context.runOnClient(c->{var cfg=NaturalityConfig.get().liquids;cfg.water=saved[0];cfg.waterfallParticles=saved[1];
                c.options.improvedTransparency().set(saved[2]);c.options.particles().set(particleStatus[0]);if(c.gui.hud.isHidden()!=saved[3])c.gui.hud.toggle();});
        }
    }
    private static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
}



















