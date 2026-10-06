package naturality.test;

import naturality.NaturalityParticles;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public final class ParticleVisibilityGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override public void runTest(ClientGameTestContext context) {
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0.5 108 -8.5 0 0");
            server.runCommand("fill -8 100 0 8 120 0 stone");
            world.getConnection().waitForClientboundPackets();context.waitTicks(3);
            context.runOnClient(c->{
                c.particleEngine.clearParticles();
                for(double z:new double[]{6.5,-30,.5}) {
                    var particle=c.particleEngine.createParticle(NaturalityParticles.WATERFALL,.5,110.5,z,0,0,0);
                    check(particle!=null && particle.isAlive(),"Hidden, embedded and behind-camera spawns are allowed");
                }
                c.particleEngine.clearParticles();
                for(int batch=0;batch<12;batch++) {
                    naturality.client.particle.FallParticleBudget.reset();
                    for(int i=0;i<64;i++)check(c.particleEngine.createParticle(NaturalityParticles.WATERFALL,.5,110,6.5,0,0,0)!=null,
                        "Spawning is permitted up to the shared limits");
                    check(c.particleEngine.createParticle(NaturalityParticles.LAVAFALL,.5,110,6.5,0,0,0)==null,
                        "Water and lava retain their shared per-tick allowance");
                }
                naturality.client.particle.FallParticleBudget.reset();
                check(c.particleEngine.createParticle(NaturalityParticles.WATERFALL,.5,110,6.5,0,0,0)==null,
                    "768 queued spray particles retain the population cap");
                c.particleEngine.clearParticles();
            });
            server.runCommand("fill -8 100 0 8 120 0 water");
            world.getConnection().waitForClientboundPackets();context.waitTicks(1);
            context.runOnClient(c->{
                check(c.particleEngine.createParticle(NaturalityParticles.WATERFALL,.5,110,6.5,0,0,0)!=null,
                    "Water no longer blocks waterfall particle spawning");
                c.particleEngine.clearParticles();
            });
        }
    }
}