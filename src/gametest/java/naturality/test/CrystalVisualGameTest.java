package naturality.test;
import naturality.Naturality;
import naturality.client.entities.endcrystals.CrystalVisuals;
import naturality.client.entities.endcrystals.CrystalVisuals.CrystalTextParticle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;
public final class CrystalVisualGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message) {if(!value) throw new AssertionError(message);}
    public void runTest(ClientGameTestContext context) {
        for(int i=0;i<1000;i++) check(CrystalVisuals.centerHeight(i)>.94 && CrystalVisuals.centerHeight(i)<1.4,"Bobbing keeps the rotated outer cube above support");
        check(Math.abs(CrystalVisuals.bob(10)-CrystalVisuals.bob((float)(10+Math.PI*20)))<.00001,"Half-vanilla-speed bobbing period");
        context.runOnClient(_ -> {
            var model=new EndCrystalModel(EndCrystalModel.createBodyLayer().bakeRoot());
            model.cube.visit(new PoseStack(),(_,_,_,cube) -> {
                for(var face:cube.polygons) {
                    var v=face.vertices()[0];check(face.normal().dot(v.x(),v.y(),v.z())<0,"Center cube normals face inward");
                }
            });
            var texture=Naturality.id("textures/entity/end_crystal.png");
            var core=new naturality.client.entities.endcrystals.CrystalCoreModel();
            check(core.renderType(texture)==naturality.client.entities.endcrystals.CrystalCoreModel.LAYER,"Core uses its independent emissive material");
            var state=new EndCrystalRenderState();state.ageInTicks=40;
            model.setupAnim(state);core.setupAnim(state);
            check(!model.cube.visible && core.cube.visible && core.outerGlass.skipDraw && core.innerGlass.skipDraw,"Core is isolated from normally lit glass and base");
            var expected=new Quaternionf().rotationY((float)Math.toRadians(60)).rotateAxis((float)Math.PI/3,(float)Math.sqrt(.5),0,(float)Math.sqrt(.5));
            var actual=new Quaternionf().rotationZYX(model.outerGlass.zRot,model.outerGlass.yRot,model.outerGlass.xRot);
            check(Math.abs(expected.dot(actual))>.9999,"Cube spin is half vanilla speed");
        });
        try(var world=TestWorlds.disposable(context).create()) {
            var server=world.getServer();server.runCommand("gamemode spectator @a");
            server.runCommand("setblock 0 100 0 obsidian");
            server.runCommand("summon end_crystal 0.5 101 0.5 {ShowBottom:1b}");
            server.runCommand("tp @a 0.5 102 -3 0 0");context.waitTicks(12);
            context.runOnClient(client -> {
                try {
                    var field=CrystalTextParticle.class.getDeclaredField("ACTIVE");field.setAccessible(true);
                    var active=(java.util.Map<?,?>)field.get(null);
                    check(active.size()==1,"One looping particle per crystal");
                    for(var particle:active.values()) check((TestFixtures.require((CrystalTextParticle)particle, "(CrystalTextParticle)particle")).isAlive(),"Crystal glyph remains alive");
                } catch(ReflectiveOperationException error) {throw new AssertionError(error);}
                for(var e:TestFixtures.level(client).entitiesForRendering()) if(e instanceof EndCrystal crystal) {
                    var state=TestFixtures.require((EndCrystalRenderState)client.getEntityRenderDispatcher().getRenderer(crystal).createRenderState(crystal,1), "state");
                    check(Math.abs(state.ageInTicks-(crystal.time+1))<.001,"Beam and model share the native age clock");
                }
            });context.takeScreenshot("crystal-custom-core-glyph");context.waitTicks(60);
            context.runOnClient(_ -> {
                try {
                    var field=CrystalTextParticle.class.getDeclaredField("ACTIVE");field.setAccessible(true);
                    check(((java.util.Map<?,?>)field.get(null)).size()==1,"Particle does not accumulate over animation loops");
                } catch(ReflectiveOperationException error) {throw new AssertionError(error);}
            });context.takeScreenshot("crystal-slow-bob-loop");
            server.runCommand("kill @e[type=end_crystal]");context.waitTicks(8);
            context.runOnClient(_ -> {
                try {
                    var field=CrystalTextParticle.class.getDeclaredField("ACTIVE");field.setAccessible(true);
                    check(((java.util.Map<?,?>)field.get(null)).isEmpty(),"Crystal removal clears its attached particle");
                } catch(ReflectiveOperationException error) {throw new AssertionError(error);}
            });
        }
    }
}

