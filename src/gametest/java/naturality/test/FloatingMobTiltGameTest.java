package naturality.test;

import naturality.client.entities.floatingmobtilt.FloatingMobTilt;
import naturality.client.entities.floatingmobtilt.FloatingMobTilt.FloatingMobTiltState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import java.util.Set;

public final class FloatingMobTiltGameTest implements FabricClientGameTest {
    public void runTest(ClientGameTestContext context) {
        Set<String> ids=Set.of("vex","allay","breeze","blaze","parrot");
        try(var world=TestWorlds.disposable(context).create()) {
            var server=world.getServer();
            server.runCommand("gamemode spectator @a"); server.runCommand("tp @a 0 103 -12 0 0");
            int x=-4;
            for(String id:ids) {
                server.runCommand("summon "+id+" "+x+" 102 0 {NoAI:1b,NoGravity:1b,Silent:1b}"); x+=2;
            }
            server.runCommand("summon pig 6 102 0 {NoAI:1b,NoGravity:1b}");
            context.waitTicks(8);
            for(int tick=0;tick<16;tick++) {
                server.runOnServer(s -> {
                    for(var e:s.overworld().getAllEntities()) if(e instanceof LivingEntity living && ids.contains(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath())) {
                        living.setDeltaMovement(.2,0,.1); living.needsSync=true;
                    }
                });
                context.waitTicks(1);
            }
            context.runOnClient(client -> {
                int count=0;
                for(var e:TestFixtures.level(client).entitiesForRendering()) if(e instanceof LivingEntity living) {
                    String id=BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
                    var renderer=client.getEntityRenderDispatcher().getRenderer(living);
                    var state=TestFixtures.require((LivingEntityRenderState)renderer.createRenderState(living,1), "(FloatingMobTiltState)state");
                    var lean=((FloatingMobTiltState)state).naturality$floatLean();
                    double angle=Math.hypot(lean.x,lean.y);
                    if(ids.contains(id)) {
                        count++;
                        if(!FloatingMobTilt.enabled(living) || angle<.05 || angle>8)
                            throw new AssertionError(id+" must have bounded movement lean: "+angle);
                    } else if(id.equals("pig") && angle!=0) throw new AssertionError("Unlisted mobs must not lean");
                }
                if(count!=5) throw new AssertionError("All five floating mob renderers must be covered");
            });
        }
    }
}
