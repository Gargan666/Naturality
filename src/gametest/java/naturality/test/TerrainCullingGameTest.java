package naturality.test;

import java.util.HashSet;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Snowy ridges, valley views and small camera movements without Sodium. */
public final class TerrainCullingGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        // Sodium owns a different visibility graph; this reproduces the native renderer report.
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) return;
        context.runOnClient(client -> client.options.renderDistance().set(10));
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("weather minecraft:overworld rain 0");
            server.runCommand("weather minecraft:overworld wind 0");
            server.runCommand("tp @a 0 170 0 0 25");
            server.runOnServer(instance -> {
                var level = instance.overworld();
                var pos = new BlockPos.MutableBlockPos();
                for (int z = 32; z < 160; z++) for (int x = -64; x < 64; x++) {
                    double ridge = 75 * Math.exp(-Math.pow((x - 24 - 12 * Math.sin(z*.12)) / 18.0, 2));
                    int height = 100 + (int)(ridge * (.65 + .35 * Math.cos(z * .09)));
                    for (int y = 96; y <= height; y++)
                        level.setBlock(pos.set(x,y,z), Blocks.STONE.defaultBlockState(), 2);
                    level.setBlock(pos.set(x,height+1,z), Blocks.SNOW.defaultBlockState(), 2);
                }
            });
            world.getConnection().waitForClientboundPackets();
            for (String camera : new String[]{"-32 145 0 -15 15", "-32 128 32 -20 0", "0 150 16 -25 20",
                    "-24 110 110 -90 -10", "-48 132 160 -135 0"}) {
                server.runCommand("tp @a " + camera);
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(100);
                context.runOnClient(client -> {
                    var level = TestFixtures.require(client.level, "level");
                    var renderer = client.levelRenderer;
                    var view = TestFixtures.require(renderer.viewArea(), "view");
                    var graph = renderer.sectionOcclusionGraph();
                    var eye = client.gameRenderer.mainCamera().position();
                    if (!client.smartCull || level.getBlockState(BlockPos.containing(eye)).isSolidRender())
                        throw new AssertionError("Fixture must exercise enabled terrain occlusion from outside solid terrain");
                    var seen = new HashSet<Long>();
                    var missing = new HashSet<Long>();
                    var invisible = new HashSet<Long>();
                    var visible = new HashSet<Long>();
                    renderer.visibleSections().forEach(s -> visible.add(s.getSectionNode()));
                    for (int yaw = -180; yaw < 180; yaw += 3) for (int pitch = -60; pitch <= 75; pitch += 3) {
                        var ray = new ClipContext(eye, eye.add(Vec3.directionFromRotation(pitch, yaw).scale(150)),
                            ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty());
                        var hit = level.clip(ray);
                        if (hit.getType() != HitResult.Type.BLOCK) continue;
                        long key = SectionPos.asLong(hit.getBlockPos());
                        seen.add(key);
                        var section = view.getRenderSectionAt(hit.getBlockPos());
                        if (section != null && graph.getNode(section) == null) missing.add(key);
                        if (section != null && client.gameRenderer.mainCamera().getCullFrustum().isVisible(
                                new net.minecraft.world.phys.AABB(hit.getBlockPos())) && !visible.contains(key)) invisible.add(key);
                    }
                    System.out.println("TERRAIN_CULLING camera="+eye+" first-hit sections="+seen.size()
                        +" missing="+missing.stream().map(SectionPos::of).toList()
                        +" in-frustum omitted="+invisible.stream().map(SectionPos::of).toList());
                    if (seen.size()<20 || !missing.isEmpty() || !invisible.isEmpty())
                        throw new AssertionError("Visible ridge surfaces were excluded at "+eye);
                    // Vanilla rebuilds its occlusion graph when the camera crosses
                    // an eight-block cell. Check rays from within that reused cell.
                    for (int corner=0;corner<8;corner++) {
                        var moved=new Vec3(Math.floor(eye.x/8)*8+((corner&1)==0?.1:7.9),
                            Math.floor(eye.y/8)*8+((corner&2)==0?.1:7.9),
                            Math.floor(eye.z/8)*8+((corner&4)==0?.1:7.9));
                        if(level.getBlockState(BlockPos.containing(moved)).isSolidRender())continue;
                        var stale=new HashSet<Long>();
                        for(int yaw=-180;yaw<180;yaw+=3)for(int pitch=-45;pitch<=75;pitch+=3) {
                            var hit=level.clip(new ClipContext(moved,moved.add(Vec3.directionFromRotation(pitch,yaw).scale(150)),
                                ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,CollisionContext.empty()));
                            if(hit.getType()!=HitResult.Type.BLOCK)continue;
                            var section=view.getRenderSectionAt(hit.getBlockPos());
                            if(section!=null && graph.getNode(section)==null)stale.add(section.getSectionNode());
                        }
                        if(!stale.isEmpty())throw new AssertionError("Small camera movement exposes culled sections: "
                            +moved+" "+stale.stream().map(SectionPos::of).toList());
                    }
                });
                context.takeScreenshot("terrain-culling-" + camera.split(" ")[1]);
            }
        }
    }
}
