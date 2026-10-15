package naturality.test;

import naturality.client.entities.ghasts.GhastSquash;
import naturality.client.entities.ghasts.GhastSquash.SquashingGhastRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.monster.Ghast;

public final class GhastSquashGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var motion = new GhastSquash.Motion();
        for (int i = 0; i < 60; i++) motion.tickMovement(.3, .3);
        if (motion.leanX(1) <= 0 || motion.leanZ(1) <= 0
                || Math.hypot(motion.leanX(1), motion.leanZ(1)) > 8)
            throw new AssertionError("Lean follows diagonal motion within the angle limit");
        for (int i = 0; i < 60; i++) motion.tickMovement(-.3, 0);
        if (motion.leanX(1) >= 0 || Math.abs(motion.leanZ(1)) > .001F)
            throw new AssertionError("Lean smoothly reverses direction");
        for (int i = 0; i < 60; i++) motion.tickMovement(0, 0);
        if (Math.hypot(motion.leanX(1), motion.leanZ(1)) > .001)
            throw new AssertionError("Stopped ghast returns upright");
        var noisy=new naturality.api.motion.GhastMotion.Motion();
        for(int tick=0;tick<80;tick++) {
            float previous=noisy.leanX(1);
            noisy.tickMovement(tick%2==0?.3:-.3,0);
            if(Math.abs(noisy.leanX(0)-previous)>.000001)
                throw new AssertionError("New velocity samples must not change the previous render endpoint");
            if(tick>60 && Math.abs(noisy.leanX(1))>.25)
                throw new AssertionError("Alternating movement noise must be damped");
        }
        for (int i = 0; i < 9; i++) motion.tick(true);
        if (Math.abs(motion.sample(1) - .12F) > .00001F) throw new AssertionError("Charge compression");
        motion.tick(false);
        if (Math.abs(motion.sample(1) - .12F) > .00001F) throw new AssertionError("Continuous release");
        for (int i = 0; i < 5; i++) motion.tick(false);
        if (motion.sample(1) >= 0) throw new AssertionError("Elastic overshoot");
        for (int i = 0; i < 25; i++) motion.tick(false);
        if (motion.sample(1) != 0) throw new AssertionError("Exact rest after release");
        try (var world = TestWorlds.disposable(context).create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0 103 -12 0 0");
            server.runCommand("summon ghast 0 102 0 {NoAI:1b,NoGravity:1b,Rotation:[180.0f,0.0f]}");
            context.waitTicks(5);
            context.takeScreenshot("ghast-rest");
            server.runOnServer(s -> {
                for (var e : s.overworld().getAllEntities()) if (e instanceof Ghast ghast) ghast.setCharging(true);
            });
            context.waitTicks(12);
            check(context, 1);
            context.takeScreenshot("ghast-charged-squash");
            server.runOnServer(s -> {
                for (var e : s.overworld().getAllEntities()) if (e instanceof Ghast ghast) ghast.setCharging(false);
            });
            context.waitTicks(6);
            check(context, -1);
            context.takeScreenshot("ghast-elastic-release");
            context.waitTicks(30);
            check(context, 0);
            server.runOnServer(s -> {
                for (var e : s.overworld().getAllEntities()) if (e instanceof Ghast ghast) {
                    ghast.setDeltaMovement(.25, 0, 0);
                    ghast.needsSync = true;
                }
            });
            context.waitTicks(10);
            context.runOnClient(client -> {
                for (var e : TestFixtures.level(client).entitiesForRendering()) if (e instanceof Ghast ghast) {
                    var renderer = client.getEntityRenderDispatcher().getRenderer(ghast);
                    var state = TestFixtures.require((SquashingGhastRenderer.State)renderer.createRenderState(ghast, 1), "state");
                    if (Math.hypot(state.leanPitch, state.leanRoll) < .1)
                        throw new AssertionError("Moving ghast render state must lean");
                }
            });
            context.takeScreenshot("ghast-movement-lean");
            server.runCommand("summon happy_ghast -5 102 0 {NoAI:1b,NoGravity:1b}");
            server.runCommand("summon happy_ghast 5 103 0 {NoAI:1b,NoGravity:1b,Age:-24000}");
            context.waitTicks(5);
            server.runOnServer(s -> {
                for (var e : s.overworld().getAllEntities())
                    if (e instanceof net.minecraft.world.entity.animal.happyghast.HappyGhast ghast) {
                        ghast.setDeltaMovement(.2, 0, 0);
                        ghast.needsSync = true;
                    }
            });
            context.waitTicks(10);
            context.runOnClient(client -> {
                int adult = 0, baby = 0;
                for (var e : TestFixtures.level(client).entitiesForRendering())
                    if (e instanceof net.minecraft.world.entity.animal.happyghast.HappyGhast ghast) {
                        var renderer = client.getEntityRenderDispatcher().getRenderer(ghast);
                        var state = TestFixtures.require((naturality.client.entities.ghasts.GhastSquash.LeaningHappyGhastRenderer.State)renderer.createRenderState(ghast, 1), "state");
                        double lean = Math.hypot(state.leanPitch, state.leanRoll);
                        if (lean < .1 || lean > 8) throw new AssertionError("Happy ghast must lean within the shared limit");
                        if (state.isBaby) baby++; else adult++;
                    }
                if (adult != 1 || baby != 1) throw new AssertionError("Both adult and baby happy ghasts must lean");
            });
            server.runCommand("tp @a 0 104 -17 0 0");
            context.waitTicks(2);
            context.takeScreenshot("happy-ghast-movement-lean");
        }
    }
    private static void check(ClientGameTestContext context, int phase) {
        context.runOnClient(client -> {
            for (var e : TestFixtures.level(client).entitiesForRendering()) if (e instanceof Ghast ghast) {
                var renderer = client.getEntityRenderDispatcher().getRenderer(ghast);
                var state = TestFixtures.require((SquashingGhastRenderer.State)renderer.createRenderState(ghast, 1), "state");
                if ((phase == 1 && state.squash < .11F) || (phase == -1 && state.squash >= 0)
                        || (phase == 0 && state.squash != 0)) throw new AssertionError("Ghast animation phase " + phase);
                return;
            }
            throw new AssertionError("Missing ghast fixture");
        });
    }
}

