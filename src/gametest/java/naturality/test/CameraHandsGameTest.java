package naturality.test;

import naturality.client.camera.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.joml.Matrix4f;

public final class CameraHandsGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public void runTest(ClientGameTestContext context) {
        var tilt = new AirborneTilt();
        tilt.tick(false, .42);
        check(tilt.sample(1) > 0, "Jump points down");
        tilt.reset(); tilt.tick(false, -.42); float shortFall = -tilt.sample(1);
        for (int i = 0; i < 50; i++) tilt.tick(false, -.42);
        check(-tilt.sample(1) > 4 && -tilt.sample(1) > shortFall, "Longer falls increase upward tilt");
        for (int i = 0; i < 60; i++) tilt.tick(false, -10);
        check(-tilt.sample(1) > 14.99 && -tilt.sample(1) <= 15, "Fast freefall reaches bounded sixfold tilt");
        for (int i = 0; i < 70; i++) tilt.tick(true, 0);
        check(tilt.sample(1) == 0, "Landing returns to exact neutral");
        for (int i = 0; i < 100; i++) tilt.tick(false, -.42, true);
        check(Math.abs(tilt.sample(1) + 2.5) < .001, "Controlled creative descent does not accrue freefall duration");
        check(ElasticMotion.elasticOut(0) == 0 && ElasticMotion.elasticOut(1) == 1 && ElasticMotion.elasticOut(.2) > 1, "Actual elasticOut curve overshoots with exact endpoints");
        check(ElasticMotion.DURATION == 18 && Math.abs(ElasticMotion.elasticOut(.2) - 1.0625) < .00001,
            "Three-times duration and half-strength elastic overshoot");
        var motion = new ElasticMotion(); motion.sample(new float[]{0}, 0);
        check(motion.sample(new float[]{1}, 1)[0] == 0, "A changed target starts continuously");
        check(motion.sample(new float[]{1}, 2)[0] < 1, "Slower easing is still approaching the target after one tick");
        for (int i = 3; i < 5; i++) motion.sample(new float[]{1}, i);
        float mid = motion.sample(new float[]{1}, 5)[0];
        check(mid > 1, "Motion overshoots elastically");
        check(motion.sample(new float[]{1}, 5)[0] == mid, "Duplicate render/rod measurement does not advance easing");
        float retarget = motion.sample(new float[]{2}, 5.01)[0];
        check(Math.abs(retarget - mid) < .1, "Retargeting remains continuous instead of restarting abruptly");
        for (int i = 6; i <= 30; i++) motion.sample(new float[]{2}, i);
        check(Math.abs(motion.sample(new float[]{2}, 30)[0] - 2) < .00001, "Elastic motion settles exactly");
        var hand = new ElasticHandPose(); hand.sample(new Matrix4f(), 0);
        hand.sample(new Matrix4f().translation(0, .2F, 0).rotateY(.7F), 1);
        for (int i = 2; i < 5; i++) hand.sample(new Matrix4f().translation(0, .2F, 0).rotateY(.7F), i);
        var pose = hand.sample(new Matrix4f().translation(0, .2F, 0).rotateY(.7F), 5);
        check(pose.m31() > .2 && Math.abs(pose.determinant() - 1) < .0001, "Hand translation overshoots while rotation remains rigid");
        var sheared = new Matrix4f().scale(1, 1, 1.2F).rotateY(.7F);
        hand.reset();
        check(hand.sample(sheared, 0).equals(sheared, .00001F), "Native stretched/sheared item transforms are preserved at rest");
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("fill -8 99 -8 8 99 8 stone");
            server.runCommand("tp @a 0 100 0 0 0");
            context.waitTicks(12);
            context.runOnClient(client -> {
                var player = client.player;
                var camera = client.gameRenderer.mainCamera();
                try {
                    var align = net.minecraft.client.Camera.class.getDeclaredMethod("alignWithEntity", float.class); align.setAccessible(true);
                    player.setOnGround(false); player.getAbilities().flying = false;
                    player.setDeltaMovement(0, -2, 0);
                    for (int i = 0; i < 60; i++) camera.tick();
                    align.invoke(camera, 1F);
                    float multiplier = ((AirborneCameraState)camera).naturality$fallMultiplier(1);
                    check(multiplier > 3 && multiplier <= 6, "Actual camera exposes stronger fall multiplier for hand lift");
                    check(Math.abs(camera.xRot() - player.getViewXRot(1) + multiplier * 2.5) < .001, "Actual camera rotation matches shared fall multiplier");
                    var renderer = client.gameRenderer.firstPersonHandsAndItemsRenderer;
                    var framePlayer = client.gameRenderer.gameRenderState().levelRenderState.playerRenderState;
                    renderer.submitHandsWithItems(1, new com.mojang.blaze3d.vertex.PoseStack(), new net.minecraft.client.renderer.SubmitNodeStorage(), framePlayer, framePlayer.firstPersonHandsAndItems);
                    var lift = renderer.getClass().getDeclaredField("naturality$lift"); lift.setAccessible(true);
                    check(Math.abs(lift.getFloat(renderer) - .055F * multiplier) < .00001, "HUD arm lift uses exactly the camera's sampled fall multiplier");
                    player.setPose(net.minecraft.world.entity.Pose.FALL_FLYING);
                    align.invoke(camera, 1F);
                    check(((AirborneCameraState)camera).naturality$fallMultiplier(1) == 0 && camera.xRot() == player.getViewXRot(1), "Elytra pose immediately clears tilt and hand lift");
                    player.setPose(net.minecraft.world.entity.Pose.STANDING);
                    player.setOnGround(true); player.setDeltaMovement(0, 0, 0);
                    camera.tick(); align.invoke(camera, 1F);
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_sword");
            server.runCommand("item replace entity @a weapon.offhand with minecraft:shield");
            context.waitTicks(15);
            context.runOnClient(client -> client.options.keyAttack.setDown(true));
            context.waitTicks(3); context.takeScreenshot("elastic-hands-swing");
            context.runOnClient(client -> client.options.keyAttack.setDown(false));
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:bow");
            context.waitTicks(12);
            context.runOnClient(client -> client.options.keyUse.setDown(true));
            context.waitTicks(15); context.takeScreenshot("elastic-hands-bow");
            context.runOnClient(client -> client.options.keyUse.setDown(false));
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:fishing_rod");
            context.waitTicks(12);
            context.runOnClient(client -> check(naturality.client.villager.FirstPersonRodTip.worldTip != null,
                "Fishing rod measurement still follows the elastic rendered pose"));
            server.runCommand("item replace entity @a weapon.offhand with minecraft:air");
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:filled_map[minecraft:map_id=0]");
            context.waitTicks(15); context.takeScreenshot("elastic-hands-map");
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:air");
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0 175 0 0 0");
            context.waitTicks(22); context.takeScreenshot("elastic-hands-falling");
        }
    }
}
