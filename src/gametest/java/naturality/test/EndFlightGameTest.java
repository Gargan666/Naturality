package naturality.test;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.ArrayList;

public final class EndFlightGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    private static Vec3 glide(LivingEntity entity,Vec3 velocity) {
        try {
            var method=LivingEntity.class.getDeclaredMethod("updateFallFlyingMovement",Vec3.class);
            method.setAccessible(true);
            return (Vec3)method.invoke(entity,velocity);
        } catch(ReflectiveOperationException e) {throw new AssertionError(e);}
    }
    private static void align(Camera camera) {
        try {
            var method=Camera.class.getDeclaredMethod("alignWithEntity",float.class);
            method.setAccessible(true);method.invoke(camera,1F);
        } catch(ReflectiveOperationException e) {throw new AssertionError(e);}
    }
    private static Vec3 headDirection(AvatarRenderer<?> renderer,AvatarRenderState state) {
        try {
            var pose=new PoseStack();
            var method=AvatarRenderer.class.getDeclaredMethod("setupRotations",AvatarRenderState.class,PoseStack.class,float.class,float.class);
            method.setAccessible(true);method.invoke(renderer,state,pose,state.bodyRot,state.scale);
            pose.scale(-1,-1,1);
            renderer.getModel().setupAnim(state);
            renderer.getModel().head.translateAndRotate(pose);
            return new Vec3(pose.last().pose().transformDirection(new Vector3f(0,0,-1)).normalize());
        } catch(ReflectiveOperationException e) {throw new AssertionError(e);}
    }
    @Override public void runTest(ClientGameTestContext context) {
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runOnServer(s -> s.getLevel(Level.END).setDragonFight(null));
            server.runCommand("gamerule minecraft:advance_weather false");
            server.runCommand("gamemode spectator @a");
            server.runCommand("execute in minecraft:the_end run tp @a 0 140 0 0 0");
            context.waitTicks(40);
            server.runCommand("execute in minecraft:the_end run fill -8 138 -8 8 138 8 end_stone");
            server.runCommand("execute in minecraft:the_end run fill -8 145 -8 8 145 8 end_stone");
            server.runCommand("execute in minecraft:the_end run tp @a 0 139 0 0 0");
            server.runCommand("item replace entity @a armor.chest with elytra");
            server.runCommand("gamemode survival @a");
            server.runCommand("effect give @a naturality:distortion infinite 0 true");
            context.waitTicks(110);
            var failures=new ArrayList<String>();
            server.runOnServer(s -> {
                var end=s.getLevel(Level.END);
                var inverted=EntityTypes.COW.create(end,EntitySpawnReason.COMMAND);
                DistortionFixtures.invert(inverted);
                var upright=EntityTypes.COW.create(s.overworld(),EntitySpawnReason.COMMAND);
                upright.setXRot(0);upright.setYRot(0);
                var fastGlide=new Vec3(0,0,4);
                fastGlide=glide(upright,fastGlide);
                if(fastGlide.horizontalDistance()<3.5)
                    failures.add("Elytra burst disappears immediately");
                for(int tick=0;tick<60;tick++)fastGlide=glide(upright,fastGlide);
                if(fastGlide.length()>2.25)
                    failures.add("Elytra excess momentum does not dissipate within three seconds");
                upright.setXRot(70);
                var dive=new Vec3(0,0,.5);
                for(int tick=0;tick<300;tick++) {
                    dive=glide(upright,dive);
                    if(dive.length()>4.500001)failures.add("Dive exceeds elytra speed limit");
                }
                if(dive.length()<3)failures.add("Diving does not build high momentum");
                if(glide(upright,new Vec3(0,-12,8)).length()>4.500001)
                    failures.add("External boost bypasses elytra speed limit");
                for(float pitch:new float[]{-45,0,35})for(double y:new double[]{-.3,.3}) {
                    inverted.setXRot(pitch);upright.setXRot(-pitch);
                    inverted.setYRot(25);upright.setYRot(25);
                    var v=new Vec3(.12,y,.2);
                    var expected=glide(upright,new Vec3(v.x,-v.y,v.z));
                    var actual=glide(inverted,v);
                    if(actual.distanceTo(new Vec3(expected.x,-expected.y,expected.z))>1.0e-6)
                        failures.add("Inverted elytra lift/dive differs from mirrored normal flight: pitch="+pitch+" y="+y);
                }
            });
            context.runOnClient(c -> {
                var wings=new net.minecraft.client.model.object.equipment.ElytraModel(
                        net.minecraft.client.model.object.equipment.ElytraModel.createLayer().bakeRoot());
                var capeState=new AvatarRenderState();
                capeState.elytraRotX=(float)Math.PI/12;
                capeState.elytraRotZ=-(float)Math.PI/12;
                wings.setupAnim(capeState);
                check(Math.abs(wings.root().xRot)<1.0e-6,"Idle elytra retains resting pose");
                capeState.capeLean=60;capeState.capeFlap=10;capeState.capeLean2=12;
                wings.setupAnim(capeState);
                check(wings.root().xRot>.5F,"Folded elytra follows cape backward sway");
                check(Math.abs(wings.root().zRot)>.01F,"Folded elytra follows cape sideways sway");
                capeState.isFallFlying=true;
                wings.setupAnim(capeState);
                check(Math.abs(wings.root().xRot)+Math.abs(wings.root().yRot)+Math.abs(wings.root().zRot)<1.0e-6,
                        "Flight clears cape motion from the shared model");
                var p=c.player;
                check(p.onGround(),"Player starts on the underside");
                var savedMovement=p.getDeltaMovement();
                p.startFallFlying();
                p.setDeltaMovement(Vec3.ZERO);
                float slowFov=p.getFieldOfViewModifier(true,1);
                p.setDeltaMovement(0,0,4);
                float fastFov=p.getFieldOfViewModifier(true,1);
                check(fastFov>slowFov+.2F,"Fast elytra flight widens FOV");
                check(p.getFieldOfViewModifier(true,0)==1F,"FOV Effects off disables flight widening");
                p.stopFallFlying();
                check(Math.abs(p.getFieldOfViewModifier(true,1)-slowFov)<1.0e-6,"Landing restores normal FOV target");
                p.setDeltaMovement(savedMovement);
                var renderer=(AvatarRenderer<net.minecraft.client.player.LocalPlayer>)(Object)c.getEntityRenderDispatcher().getRenderer(p);
                for(float yaw:new float[]{-135,35,135})for(float pitch:new float[]{-40,20,50}) {
                    p.setYRot(yaw);p.yRotO=yaw;p.yHeadRot=p.yHeadRotO=yaw;p.yBodyRot=p.yBodyRotO=yaw-25;
                    p.setXRot(pitch);p.xRotO=pitch;
                    var state=(AvatarRenderState)renderer.createRenderState(p,1);
                    for(var cameraType:new CameraType[]{CameraType.THIRD_PERSON_BACK,CameraType.THIRD_PERSON_FRONT}) {
                        c.options.setCameraType(cameraType);align(c.gameRenderer.mainCamera());
                        var forward=new Vec3(c.gameRenderer.mainCamera().forwardVector());
                        if(cameraType.isMirrored())forward=forward.scale(-1);
                        if(headDirection(renderer,state).dot(forward)<.999)
                            failures.add("Third-person model look disagrees with camera: "+cameraType);
                    }
                    if(headDirection(renderer,state).dot(p.getViewVector(1))<.999)
                        failures.add("Rendered head faces away from the camera look: yaw="+yaw+" pitch="+pitch);
                }
                p.setYRot(0);p.yRotO=0;p.yHeadRot=p.yHeadRotO=0;p.yBodyRot=p.yBodyRotO=0;p.setXRot(0);p.xRotO=0;
                c.options.setCameraType(CameraType.FIRST_PERSON);
                p.setPose(Pose.FALL_FLYING);align(c.gameRenderer.mainCamera());
                if(c.gameRenderer.mainCamera().position().y>=145)
                    failures.add("Opening the wings moves the camera through the supporting ceiling");
                p.setPose(Pose.STANDING);align(c.gameRenderer.mainCamera());
            });
            check(failures.isEmpty(),String.join("\n",failures));
            context.runOnClient(c -> {
                c.player.setYRot(35);c.player.setXRot(20);c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            });
            context.waitTicks(3);
            context.takeScreenshot("end-standing-inverted-back");
            context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(3);
            context.takeScreenshot("end-standing-inverted-front");
            context.runOnClient(c -> {c.player.setYRot(0);c.player.setXRot(0);c.options.setCameraType(CameraType.FIRST_PERSON);});
            context.runOnClient(c -> c.options.keyJump.setDown(true));
            context.waitTicks(2);
            context.runOnClient(c -> c.options.keyJump.setDown(false));
            context.waitTicks(2);
            context.runOnClient(c -> c.options.keyJump.setDown(true));
            context.waitTicks(2);
            context.runOnClient(c -> {c.options.keyJump.setDown(false);check(c.player.isFallFlying(),"Double jump activates elytra off the ceiling");});
            context.waitTicks(2);
            server.runOnServer(s -> check(s.getLevel(Level.END).players().getFirst().isFallFlying(),"Server accepts upside-down elytra activation"));
            server.runOnServer(s -> {
                var inverted=s.getLevel(Level.END).players().getFirst();
                var upright=EntityTypes.COW.create(s.overworld(),EntitySpawnReason.COMMAND);
                try {
                    var flag=Entity.class.getDeclaredMethod("setSharedFlag",int.class,boolean.class);
                    flag.setAccessible(true);flag.invoke(upright,7,true);
                } catch(ReflectiveOperationException e) {throw new AssertionError(e);}
                var old=inverted.getDeltaMovement();
                inverted.setDeltaMovement(.1,.35,.2);upright.setDeltaMovement(.1,-.35,.2);
                var invertedWings=new ElytraAnimationState(inverted);var uprightWings=new ElytraAnimationState(upright);
                for(int i=0;i<4;i++){invertedWings.tick();uprightWings.tick();}
                check(Math.abs(invertedWings.getRotX(1)-uprightWings.getRotX(1))<1e-6
                    && Math.abs(invertedWings.getRotZ(1)-uprightWings.getRotZ(1))<1e-6,"Wing animation follows gravity-relative descent");
                inverted.setDeltaMovement(old);
            });
            server.runCommand("execute in minecraft:the_end run tp @a 0 165 0 0 0");
            world.getConnection().waitForClientboundPackets();
            context.runOnClient(c -> {c.player.setDeltaMovement(0,.35,0);c.player.setOnGround(false);c.player.startFallFlying();});
            context.waitTicks(8);
            context.runOnClient(c -> {
                check(c.player.isFallFlying() && c.player.getDeltaMovement().horizontalDistance()>.03,
                    "Upward falling converts into forward glide instead of stalling");
                c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            });
            context.waitTicks(3);
            context.takeScreenshot("end-elytra-inverted-back");
            context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(3);
            context.takeScreenshot("end-elytra-inverted-front");
            server.runCommand("item replace entity @a armor.chest with air");
            server.runCommand("effect clear @e naturality:distortion");
            context.waitTicks(90);
            context.runOnClient(c -> {
                check(!c.player.isFallFlying(),"Removing elytra still ends flight");
                c.options.setCameraType(CameraType.FIRST_PERSON);
            });
        }
    }
}
