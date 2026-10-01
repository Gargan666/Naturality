package naturality.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.particles.ParticleTypes;

public final class RainSplashGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("fill -3 99 -3 3 99 3 stone");
            server.runCommand("setblock 1 100 0 stone_slab");
            server.runCommand("tp @a 0 101 -2 0 35");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                for (int i=0;i<32;i++) {
                    double top=i%2==0?100:100.5;
                    var rain=client.particleEngine.createParticle(ParticleTypes.RAIN,
                        i%2==0?0.5:1.5,top+0.01,0.5,0,0,0);
                    for(int tick=0;tick<45 && rain.isAlive();tick++) {
                        rain.tick();
                        if(rain.getBoundingBox().minY<=top)
                            throw new AssertionError("Ground rain must disappear before landing, including on slabs");
                    }
                    if(rain.isAlive()) throw new AssertionError("Rain splash must expire");
                }
                for(int i=0;i<20;i++) client.particleEngine.createParticle(ParticleTypes.RAIN,
                    -1+i*0.1,100.01,0.5,0,0,0);
            });
            context.waitTicks(2);
            context.takeScreenshot("rain-splash-fade");
        }
    }
}
