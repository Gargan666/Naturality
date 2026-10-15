package naturality.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.Pose;

public final class DistortionInteractionGameTest implements FabricClientGameTest {
    public static volatile boolean captureLanding;
    public static volatile double landingY;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override public void runTest(ClientGameTestContext context) {
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("gamemode spectator @a");server.runCommand("tp @a 0 141 0");context.waitTicks(30);
            server.runCommand("fill -8 138 -8 8 150 8 air");
            server.runCommand("fill -8 138 -8 8 138 8 stone");server.runCommand("fill -8 145 -8 8 145 8 sea_lantern");
            server.runCommand("gamemode survival @a");
            server.runCommand("effect give @a naturality:distortion infinite 0 true");context.waitTicks(120);
            context.runOnClient(c->{
                var p=TestFixtures.require(c.player, "p");var camera=c.gameRenderer.mainCamera();
                try {
                    var align=net.minecraft.client.Camera.class.getDeclaredMethod("alignWithEntity",float.class);align.setAccessible(true);
                    for(var pose:new Pose[]{Pose.CROUCHING,Pose.STANDING,Pose.FALL_FLYING,Pose.STANDING}) {
                        align.invoke(camera,0F);double eye=camera.position().y;
                        double feet=p.getBoundingBox().maxY;double previousFeet=p.yo+p.getBbHeight();
                        p.setPose(pose);
                        check(Math.abs(p.getBoundingBox().maxY-feet)<1e-6,"Visual pose transition preserves current feet");
                        check(Math.abs(p.yo+p.getBbHeight()-previousFeet)<1e-6,"Previous-frame feet stay in the same interpolation frame");
                        align.invoke(camera,0F);check(Math.abs(camera.position().y-eye)<1e-5,"Pose change does not snap the interpolated camera: "+pose);
                    }
                    var inventory=net.minecraft.client.gui.screens.inventory.InventoryScreen.class.getDeclaredMethod("extractRenderState",net.minecraft.world.entity.LivingEntity.class);
                    inventory.setAccessible(true);
                    var preview=(net.minecraft.client.renderer.entity.state.AvatarRenderState)inventory.invoke(null,p);
                    check(((naturality.client.weather.EndGravityRenderState)preview).naturality$inversion()==0,"Inventory preview uses its own upright orientation");
                    check(!naturality.client.weather.GravityPreviewContext.active(),"Inventory rendering restores the world context");
                    var renderer=c.getEntityRenderDispatcher().getRenderer(p);
                    var state=TestFixtures.require((net.minecraft.client.renderer.entity.state.AvatarRenderState)renderer.createRenderState(p,1), "(naturality.client.weather.EndGravityRenderState)state");
                    check(((naturality.client.weather.EndGravityRenderState)state).naturality$inversion()>.99,"World rendering remains inverted after inventory extraction");
                    state.isCrouching=true;
                    check(((net.minecraft.client.renderer.entity.player.AvatarRenderer<?>)renderer).getRenderOffset(state).y>0,"Inverted crouch model offset points toward the ceiling");
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}
            });
            for(int iteration=0;iteration<4;iteration++) {
                context.runOnClient(c->c.options.keyShift.setDown(true));context.waitTicks(5);
                context.runOnClient(c->check(TestFixtures.player(c).getPose()==Pose.CROUCHING && Math.abs(TestFixtures.player(c).getBoundingBox().maxY-145)<.01,"Sneaking keeps ceiling footing"));
                if(iteration==0) {
                    context.runOnClient(c->{if(!c.gui.hud.isHidden())c.gui.hud.toggle();c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);});
                    context.waitTicks(2);context.takeScreenshot("distortion-crouch-ceiling-contact");
                    context.runOnClient(c->{if(c.gui.hud.isHidden())c.gui.hud.toggle();c.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);});
                }
                context.runOnClient(c->c.options.keyShift.setDown(false));context.waitTicks(5);
                context.runOnClient(c->check(TestFixtures.player(c).getPose()==Pose.STANDING && TestFixtures.player(c).onGround(),"Uncrouching does not produce a phantom jump"));
                server.runOnServer(s->check(s.overworld().players().getFirst().onGround(),"Server preserves footing across rapid crouch changes"));
            }
            server.runOnServer(s->{
                var p=s.overworld().players().getFirst();p.connection.resetPosition();
                double before=p.getY();p.setPose(Pose.CROUCHING);
                try {
                    for(String name:new String[]{"firstGoodY","lastGoodY"}) {
                        var f=p.connection.getClass().getDeclaredField(name);f.setAccessible(true);
                        check(Math.abs(f.getDouble(p.connection)-p.getY())<1e-6,"Pose change updates movement baseline "+name);
                    }
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}
                p.setPermanentlyInvulnerable(true);
                try {
                    var fall=net.minecraft.world.entity.LivingEntity.class.getDeclaredMethod("checkFallDamage",double.class,boolean.class,net.minecraft.world.level.block.state.BlockState.class,net.minecraft.core.BlockPos.class);
                    fall.setAccessible(true);captureLanding=true;landingY=Double.NaN;p.fallDistance=20;
                    fall.invoke(p,1,true,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),new net.minecraft.core.BlockPos(0,145,0));
                    check(Math.abs(landingY-(p.getBoundingBox().maxY-.01))<1e-6,"Hard landing debris comes from the ceiling-facing feet");
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}finally{captureLanding=false;}
                check(p.getY()>before,"Inverted crouch changes body origin while preserving the feet");p.setPose(Pose.STANDING);
            });
            server.runCommand("item replace entity @a armor.chest with elytra");world.getConnection().waitForClientboundPackets();
            context.runOnClient(c->c.options.keyJump.setDown(true));context.waitTicks(2);
            context.runOnClient(c->c.options.keyJump.setDown(false));context.waitTicks(2);
            server.runCommand("item replace entity @a armor.chest with elytra");world.getConnection().waitForClientboundPackets();
            context.runOnClient(c->c.options.keyJump.setDown(true));context.waitTicks(2);
            context.runOnClient(c->{c.options.keyJump.setDown(false);check(TestFixtures.player(c).isFallFlying(),"Second jump press activates inverted elytra");});
            context.waitTicks(6);
            server.runOnServer(s->check(s.overworld().players().getFirst().isFallFlying(),"Server accepts and keeps inverted elytra active"));
        }
    }
}