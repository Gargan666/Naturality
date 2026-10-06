package naturality.test;

import naturality.client.fog.FogCulling;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

public final class FogCullingGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext context) {
        check(FogCulling.hidden(0,0,0,15,100,20,-1,-1,22,1,1), "Fully obscured rain bounds");
        check(!FogCulling.hidden(0,0,0,15,100,14,-1,-1,20,1,1), "Bounds crossing rain boundary stay visible");
        check(FogCulling.hidden(0,0,0,1000,20,-1,25,-1,1,30,1), "Cylindrical fog includes vertical distance");
        check(!FogCulling.hidden(0,0,0,1000,20,14,-1,14,15,1,15), "Cylinder differs from sphere at diagonal");
        check(!FogCulling.hidden(0,0,0,15,20,Double.NaN,0,0,50,1,1), "Malformed bounds fail open");
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode spectator @a");
            world.getServer().runCommand("tp @a 0 105 0 0 0");
            world.getServer().runCommand("fill -16 96 8 16 102 64 stone");
            world.getServer().runCommand("weather minecraft:overworld rain 100");
            world.getServer().runCommand("weather minecraft:overworld temperature 50");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(240);
            context.runOnClient(client -> {
                var camera = client.gameRenderer.mainCamera();
                var eye = camera.position();
                check(FogCulling.hidden(eye.x + 40,eye.y,eye.z,eye.x + 42,eye.y + 2,eye.z + 2), "Current rain fog is captured");
                var cow = EntityTypes.COW.create(client.level, EntitySpawnReason.COMMAND);
                // Bypass Sodium's independent section occlusion for this isolated fog test.
                cow.setCustomName(net.minecraft.network.chat.Component.literal("Fog test"));
                cow.setCustomNameVisible(true);
                cow.setPos(eye.x,eye.y,eye.z + 40);
                var frustum = new Frustum(new Matrix4f(), new Matrix4f()) {
                    @Override public boolean isVisible(AABB bounds) { return true; }
                };
                var renderer = client.getEntityRenderDispatcher().getRenderer(cow);
                check(!renderer.shouldRender(cow,frustum,eye.x,eye.y,eye.z,1), "Hidden entity is culled");
                var fog = new FogData();
                fog.color.set(1,1,1,1);
                fog.environmentalStart = 12; fog.environmentalEnd = 15;
                fog.renderDistanceStart = 100; fog.renderDistanceEnd = 128;
                FogCulling.capture(client.level,camera,fog,false);
                check(renderer.shouldRender(cow,frustum,eye.x,eye.y,eye.z,1), "Disabled fog restores same entity");
                check(cow.isAlive(), "Render culling does not delete entity");
                FogCulling.capture(client.level,camera,fog,true);
                int sectionY=((int)eye.y>>4)+2;
                check(FogCulling.sectionHidden(0,sectionY<<4,0,0), "Distant owner section is behind fog");
                check(!FogCulling.sectionHidden(0,sectionY<<4,0,34), "Displaced snow near camera keeps its owner section");
                cow.setPos(eye.x,eye.y,eye.z + 13);
                check(renderer.shouldRender(cow,frustum,eye.x,eye.y,eye.z,1), "Boundary entity stays visible");
            });
            context.waitTicks(3);
            context.takeScreenshot("fog-culling-rain");
            world.getServer().runCommand("weather minecraft:overworld rain 0");
            context.waitTicks(240);
            context.takeScreenshot("fog-culling-clear");
        }
    }
}
