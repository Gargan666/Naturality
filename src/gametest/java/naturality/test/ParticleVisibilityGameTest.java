package naturality.test;

import naturality.NaturalityParticles;
import naturality.client.particle.ParticleOcclusion;
import naturality.client.particle.ParticleVisibility;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ParticleVisibilityGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override public void runTest(ClientGameTestContext context) {
        var eye=new Vec3(.5,.5,-8.5);
        var box=new AABB(-.25,-.25,5,1.25,1.25,6);
        check(ParticleOcclusion.hidden(eye,box,p->p.getZ()==0),"Opaque wall covers the entire sprite volume");
        check(!ParticleOcclusion.hidden(eye,box,p->false),"Open sightline remains visible");
        check(!ParticleOcclusion.hidden(eye,new AABB(-5,-5,5,6,6,6),
            p->p.getZ()==0 && p.getX()==0 && p.getY()==0),"A hidden centre does not hide large visible edges");
        check(!ParticleOcclusion.hidden(eye,new AABB(-5,-5,5,6,6,6),
            p->p.getZ()==0 && !(p.getX()==1 && p.getY()==1)),"Off-centre wall holes must keep particles visible");
        check(!ParticleOcclusion.hidden(new Vec3(.5,.5,5.5),box,p->true),"Camera inside particle volume fails open");
        check(ParticleOcclusion.hidden(new Vec3(-8.5,.5,.5),new AABB(5,-.25,-.25,6,1.25,1.25),
            p->p.getX()==0),"X-facing wall coverage");
        check(ParticleOcclusion.hidden(new Vec3(.5,8.5,.5),new AABB(-.25,-6,-.25,1.25,-5,1.25),
            p->p.getY()==0),"Floor coverage with negative coordinates");

        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0.5 108 -8.5 0 0");
            world.getConnection().waitForClientboundPackets();context.waitTicks(3);
            Particle[] existing=new Particle[1];
            context.runOnClient(c->{
                ParticleVisibility.reset();
                existing[0]=c.particleEngine.createParticle(NaturalityParticles.WATERFALL,.5,110,6.5,0,0,0);
                check(existing[0]!=null && existing[0].isAlive(),"Visible waterfall is created");
                existing[0].setParticleSpeed(0,0,0);
            });
            server.runCommand("fill -8 100 0 8 120 0 stone");
            world.getConnection().waitForClientboundPackets();context.waitTicks(6);
            context.runOnClient(c->{
                check(!existing[0].isAlive(),"A newly hidden waterfall is removed before its 24-tick lifetime");
                ParticleVisibility.reset();
                for(var type:new net.minecraft.core.particles.SimpleParticleType[]{NaturalityParticles.WATERFALL,
                    NaturalityParticles.WATERFALL_BIG,NaturalityParticles.LAVAFALL,ParticleTypes.SMOKE})
                    check(c.particleEngine.createParticle(type,.5,110,6.5,0,0,0)==null,"Hidden spawn is rejected: "+type);
                int before=ParticleVisibility.stats().regionTests();
                for(int i=0;i<100;i++)check(c.particleEngine.createParticle(NaturalityParticles.WATERFALL,
                    .5,110,6.5,0,0,0)==null,"Hidden dense groups never enter the engine");
                check(ParticleVisibility.stats().regionTests()==before,"Dense groups share visibility results");
                check(ParticleVisibility.stats().rejectedSpawns()>=104,"Spawn rejection is measurable");
                System.out.println("Particle visibility dense group: "+ParticleVisibility.stats());
                var embedded=c.particleEngine.createParticle(ParticleTypes.SMOKE,.5,110.5,.5,0,0,0);
                check(embedded==null,"Sprites fully inside opaque blocks are rejected");
                var hole=new net.minecraft.core.BlockPos(0,109,0);
                var original=c.level.getBlockState(hole);
                try {
                    c.level.setBlock(hole,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                    var revealed=c.particleEngine.createParticle(NaturalityParticles.WATERFALL,.5,110,6.5,0,0,0);
                    check(revealed!=null,"Opening a hole invalidates cached occlusion immediately, within the same tick");
                    revealed.remove();
                } finally {c.level.setBlock(hole,original,3);}
                check(c.particleEngine.createParticle(NaturalityParticles.WATERFALL,.5,110,6.5,0,0,0)==null,
                    "Closing the hole restores spawn rejection without stale visible cache entries");
            });
            for(String material:new String[]{"glass","oak_leaves[persistent=true]","water","barrier","air"}) {
                server.runCommand("fill -8 100 0 8 120 0 "+material);
                world.getConnection().waitForClientboundPackets();context.waitTicks(1);
                context.runOnClient(c->{
                    ParticleVisibility.reset();
                    var visible=c.particleEngine.createParticle(ParticleTypes.SMOKE,.5,110,6.5,0,0,0);
                    if(material.equals("water"))check(c.particleEngine.createParticle(NaturalityParticles.WATERFALL,.5,110,6.5,0,0,0)==null,
                        "Water hides waterfall spray specifically");
                    check(visible!=null && visible.isAlive(),"Particles remain visible through "+material);
                    visible.remove();
                });
            }
            server.runCommand("fill -8 100 0 8 120 0 stone");
            server.runCommand("fill -1 109 0 1 111 0 air");
            world.getConnection().waitForClientboundPackets();context.waitTicks(1);
            context.runOnClient(c->{
                ParticleVisibility.reset();
                var visible=c.particleEngine.createParticle(NaturalityParticles.WATERFALL_BIG,.5,110,6.5,0,0,0);
                check(visible!=null,"A wall opening prevents false occlusion");visible.remove();
                for(int i=0;i<2000;i++)ParticleVisibility.hidden(c.level,i,130,10,i+.5,130.5,10.5,true);
                check(ParticleVisibility.stats().regionTests()<=256 && ParticleVisibility.stats().blockQueries()<=16384,
                    "Visibility work is bounded in particle-heavy scenes");
                c.particleEngine.clearParticles();
                check(ParticleVisibility.stats().regionTests()==0,"Engine clear/resource reload releases cached visibility");
                check(c.particleEngine.createParticle(ParticleTypes.SMOKE,.5,110,-30,0,0,0)==null,
                    "Behind-camera particles are rejected");
                int accepted=0;
                for(int i=0;i<1000;i++)if(c.particleEngine.createParticle(NaturalityParticles.WATERFALL,.5,110,-2,0,0,0)!=null)accepted++;
                check(accepted==64,"Shared fall budget admits 64 particles per tick: "+accepted);
                check(c.particleEngine.createParticle(NaturalityParticles.LAVAFALL,.5,110,-2,0,0,0)==null,
                    "Lava shares the water spawning budget");
                for(int batch=1;batch<12;batch++) {
                    naturality.client.particle.FallParticleBudget.reset();
                    for(int i=0;i<64;i++)check(c.particleEngine.createParticle(NaturalityParticles.WATERFALL,.5,110,-2,0,0,0)!=null,
                        "Population limit includes pending particles");
                }
                naturality.client.particle.FallParticleBudget.reset();
                check(c.particleEngine.createParticle(NaturalityParticles.WATERFALL,.5,110,-2,0,0,0)==null,
                    "768 queued falls exhaust the live population limit");
                c.particleEngine.clearParticles();
            });
        } finally {context.runOnClient(c->ParticleVisibility.reset());}
    }
}
