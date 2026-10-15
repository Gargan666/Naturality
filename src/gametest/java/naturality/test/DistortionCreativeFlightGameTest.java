package naturality.test;

import naturality.systems.gravity.EndGravity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.phys.Vec3;

public final class DistortionCreativeFlightGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override public void runTest(ClientGameTestContext context) {
        try(var world=TestWorlds.disposable(context).create()) {
            var server=world.getServer();server.runCommand("gamemode creative @a");server.runCommand("tp @a 0 180 0");
            server.runOnServer(s->{var p=s.overworld().players().getFirst();p.getAbilities().flying=true;p.onUpdateAbilities();});
            context.waitTicks(20);
            server.runCommand("effect give @a naturality:distortion infinite 0 true");context.waitTicks(100);
            context.runOnClient(c->{
                check(TestFixtures.player(c).getAbilities().flying && EndGravity.inverted(TestFixtures.player(c)),"Creative flight retains Distortion");
                check(c.gameRenderer.mainCamera().rotation().transform(new org.joml.Vector3f(0,1,0)).y<-.99,"Flying creative camera is upside down");
                check(EndGravity.softenUpwardMotion(TestFixtures.player(c),1)==1,"Intentional creative flight is not constrained by the upward-fall spring");
                TestFixtures.player(c).setDeltaMovement(Vec3.ZERO);c.options.keyJump.setDown(true);
            });
            context.waitTicks(3);
            context.runOnClient(c->{c.options.keyJump.setDown(false);check(TestFixtures.player(c).getDeltaMovement().y<0,"Jump flies toward gravity-local up");TestFixtures.player(c).setDeltaMovement(Vec3.ZERO);c.options.keyShift.setDown(true);});
            context.waitTicks(3);
            context.runOnClient(c->{c.options.keyShift.setDown(false);check(TestFixtures.player(c).getDeltaMovement().y>0,"Sneak flies toward gravity-local down");});
            server.runOnServer(s->check(s.overworld().players().getFirst().getAbilities().flying && EndGravity.inverted(s.overworld().players().getFirst()),"Server agrees about inverted creative flight"));
            server.runCommand("effect clear @a naturality:distortion");context.waitTicks(100);
            context.runOnClient(c->check(TestFixtures.player(c).getAbilities().flying && EndGravity.inversion(TestFixtures.player(c),false)==0,"Effect expiry restores normal orientation without stopping creative flight"));
        }
    }
}