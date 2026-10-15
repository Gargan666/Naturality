package naturality.test;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.List;
import naturality.client.render.*;
import naturality.client.snow.SnowSpritePixels;
import naturality.client.snow.SnowTextureAtlas;
import naturality.config.NaturalityConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RenderDetailGameTest implements FabricClientGameTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext context) {
        var settings = NaturalityConfig.get().effects;
        boolean leaves = settings.distantLeaves, textures = settings.distantTextures;
        int[] distance = {12};
        context.runOnClient(client -> { distance[0] = client.options.renderDistance().get(); client.options.renderDistance().set(12); });
        settings.distantLeaves = settings.distantTextures = true;
        try {
            check(RenderDetail.tier(159, 0) == 0 && RenderDetail.tier(161, 0) == 1, "Leaves begin beyond their entry distance");
            check(RenderDetail.tier(140, 1) == 1 && RenderDetail.tier(127, 1) == 0, "Leaf hysteresis prevents boundary rebuild churn");
            check(RenderDetail.tier(193, 0) == 2 && RenderDetail.tier(180, 2) == 2 && RenderDetail.tier(159, 2) == 1,
                "Small-detail hysteresis preserves the return threshold");
            var eye = new Vec3(.5, .5, 0);
            var blocker = new BlockPos(0, 0, 2);
            check(OcclusionCulling.covers(eye, new AABB(0, 0, 20, 1, 1, 21), blocker), "A cube hides the whole distant box");
            check(!OcclusionCulling.covers(eye, new AABB(0, 0, 20, 10, 1, 21), blocker), "Partly visible bounds must remain");
            check(!OcclusionCulling.covers(eye, new AABB(0, 0, 1, 1, 1, 2), blocker), "An occluder behind the target cannot hide it");
            check(!OcclusionCulling.covers(new Vec3(.5, .5, 2.5), new AABB(0, 0, 20, 1, 1, 21), blocker), "A camera inside the blocker is conservative");
            try (var image = new NativeImage(2, 1, true)) {
                image.setPixel(0, 0, 0xFF204060); image.setPixel(1, 0, 0x00FFFFFF);
                check(DistantTextures.average(image, 2, 1) == 0xFF204060, "Invisible texel RGB cannot brighten the average");
                image.setPixel(1, 0, 0xFF6080A0);
                check(DistantTextures.average(image, 2, 1) == 0xFF406080, "Average color matches both opaque texels");
            }
            try (var world = context.worldBuilder().create()) {
                context.runOnClient(TextureLodChecks::verify);
                var server = world.getServer();
                server.runCommand("gamemode spectator @a");
                server.runCommand("time set 6000");
                server.runCommand("gamerule minecraft:random_tick_speed 0");
                server.runCommand("fill 0 100 0 2 102 2 oak_leaves[persistent=true]");
                server.runCommand("setblock 5 100 0 stone");
                server.runCommand("tp @a 1 102 -5 0 0");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(5);
                context.runOnClient(client -> {
                    var pos = new BlockPos(1, 101, 1);
                    var state = client.level.getBlockState(pos);
                    var model = client.getModelManager().getBlockStateModelSet().get(state);
                    check(model instanceof DistanceModel, "Leaf model has the LOD wrapper");
                    BlockStateModel original = unwrap(model);
                    view(client, new Vec3(1, 101, -5));
                    check(sameGeometry(collect(client, pos, state, model), collect(client, pos, state, original)), "Nearby leaf geometry, tint and light are identical");
                    verifyPair(client, pos, state, model, original);
                    int near = countCube(client);
                    view(client, new Vec3(1, 101, -200));
                    int far = countCube(client);
                    check(near == 162 && far == 54, "A 3x3x3 leaf cube removes all 108 internal faces: " + near + " -> " + far);
                    System.out.println("Distant leaf mesh: " + near + " -> " + far + " quads (same outer surface)");
                    var stone = new BlockPos(5, 100, 0);
                    var stoneState = client.level.getBlockState(stone);
                    var stoneModel = client.getModelManager().getBlockStateModelSet().get(stoneState);
                    view(client, new Vec3(1, 101, -400));
                    var distantStone = collect(client, stone, stoneState, stoneModel);
                    verifyPair(client, stone, stoneState, stoneModel, unwrap(stoneModel));
                    view(client, new Vec3(1, 101, -5));
                    check(distantStone.equals(collect(client, stone, stoneState, stoneModel)),
                        "Texture fading does not alter or rebuild stone geometry/UVs with camera distance");
                    view(client, new Vec3(1, 101, -1000));
                    check(distantStone.equals(collect(client, stone, stoneState, stoneModel)),
                        "Terrain still has its original block mesh beyond both removed geometry thresholds");
                    settings.distantTextures = false;
                    check(collect(client, stone, stoneState, stoneModel).equals(collect(client, stone, stoneState, unwrap(stoneModel))),
                        "Disabling average textures restores the original atlas coordinates");
                    settings.distantTextures = true;
                    view(client, new Vec3(1, 101, -5));
                    check(sameGeometry(collect(client, pos, state, model), collect(client, pos, state, original)), "Returning nearby restores exact leaf detail");
                    RenderDetail.view(client.level, client.gameRenderer.mainCamera().position());
                });
                // Exercise real chunk compilation and both rendering pipelines.
                context.waitTicks(15);
                world.getConnection().waitForChunksRender(false);
                context.takeScreenshot("render-detail-near-leaves");
                context.runOnClient(client -> { client.options.renderDistance().set(24); client.options.broadcastOptions(); });
                server.runOnServer(s -> {
                    for (int x = -1; x <= 3; x++) for (int z = 11; z <= 20; z++) s.overworld().getChunk(x, z);
                });
                server.runCommand("fill -12 100 180 12 115 192 oak_leaves[persistent=true]");
                server.runCommand("fill 30 100 310 54 110 320 stone");
                server.runCommand("tp @a 0 108 0 0 0");
                world.getConnection().waitForClientboundPackets();
                context.waitFor(client -> client.level.getBlockState(new BlockPos(40, 105, 310)).is(Blocks.STONE), 1200);
                context.waitTicks(40);
                world.getConnection().waitForChunksRender(false);
                context.takeScreenshot("render-detail-distant-leaves");
                var reload = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
                context.runOnClient(c -> reload.set(c.reloadResourcePacks()));
                context.waitFor(c -> reload.get().isDone());
                reload.get().join();
                context.runOnClient(TextureLodChecks::verify);
                if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
                    context.runOnClient(client -> { client.options.renderDistance().set(8); client.options.broadcastOptions(); });
                    server.runCommand("tp @a 0.5 102 0.5 0 0");
                    server.runCommand("setblock 0 103 2 stone");
                    int[] id = {0};
                    server.runOnServer(s -> {
                        var pig = net.minecraft.world.entity.EntityTypes.PIG.create(s.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                        pig.setPos(.5, 103, 60.5); pig.setNoAi(true); pig.setNoGravity(true);
                        s.overworld().addFreshEntity(pig); id[0] = pig.getId();
                    });
                    world.getConnection().waitForClientboundPackets();
                    context.waitTicks(35);
                    world.getConnection().waitForChunksRender(false);
                    // Resource reload uploads fresh meshes; their arrival fade
                    // uses wall time rather than gametest's accelerated ticks.
                    context.waitFor(client -> {
                        var pig = client.level.getEntity(id[0]);
                        if (pig == null) return false;
                        for (int attempt = 0; attempt < 4; attempt++) {
                            OcclusionCulling.beginFrame();
                            if (OcclusionCulling.hidden(pig, pig.getBoundingBox())) return true;
                        }
                        return false;
                    }, 600);
                    context.runOnClient(client -> {
                        var pig = client.level.getEntity(id[0]);
                        check(pig != null, "Occlusion fixture arrived on client");
                        boolean hidden = false;
                        for (int attempt = 0; attempt < 4; attempt++) { OcclusionCulling.beginFrame(); hidden |= OcclusionCulling.hidden(pig, pig.getBoundingBox()); }
                        check(hidden, "A fully covered pig is culled behind a verified uploaded stone cube");
                        pig.setCustomName(net.minecraft.network.chat.Component.literal("Visible name"));
                        check(!OcclusionCulling.hidden(pig, pig.getBoundingBox()), "Nameplates retain their original visibility");
                        pig.setCustomName(null);
                        client.level.setBlock(new BlockPos(0, 103, 2), Blocks.GLASS.defaultBlockState(), 3);
                        check(!OcclusionCulling.hidden(pig, pig.getBoundingBox()), "Replacing the blocker with glass reveals the entity immediately");
                        client.level.setBlock(new BlockPos(0, 103, 2), Blocks.STONE.defaultBlockState(), 3);
                        for (int attempt = 0; attempt < 4; attempt++) {
                            OcclusionCulling.beginFrame();
                            check(!OcclusionCulling.hidden(pig, pig.getBoundingBox()), "An edited blocker cannot cull until its replacement mesh uploads");
                        }
                    });
                }
            }
        } finally {
            settings.distantLeaves = leaves; settings.distantTextures = textures;
            context.runOnClient(client -> client.options.renderDistance().set(distance[0]));
        }
    }
    private static void view(Minecraft client, Vec3 eye) { RenderDetail.view(null, Vec3.ZERO); RenderDetail.view(client.level, eye); }
    private static boolean sameGeometry(List<List<Integer>> a,List<List<Integer>> b) {
        if(a.size()!=b.size())return false;
        for(int q=0;q<a.size();q++)for(int i=0;i<a.get(q).size();i++) {
            int field=(i-1)%7;
            if(i>0 && (field==3 || field==4))continue;
            if(!a.get(q).get(i).equals(b.get(q).get(i)))return false;
        }
        return true;
    }
    private static void verifyPair(Minecraft client,BlockPos pos,BlockState state,BlockStateModel model,BlockStateModel original) {
        var expected=collect(client,pos,state,original);
        var actual=collect(client,pos,state,model);
        check(sameGeometry(actual,expected),"Texture LOD preserves geometry and all vertex metadata");
        for(int q=0;q<actual.size();q++) {
            var a=actual.get(q);var b=expected.get(q);
            float u=0,v=0,sourceU=0,sourceV=0;
            for(int i=0;i<4;i++) {
                u+=Float.intBitsToFloat(a.get(4+i*7));v+=Float.intBitsToFloat(a.get(5+i*7));
                sourceU+=Float.intBitsToFloat(b.get(4+i*7));sourceV+=Float.intBitsToFloat(b.get(5+i*7));
            }
            var source=DistantTextures.find(sourceU/4,sourceV/4);
            var copied=SnowTextureAtlas.findComposite(u/4,v/4);
            check(source!=null && copied!=null,"Static block has a paired atlas image");
            var sourceImage=((SnowSpritePixels)source.contents()).naturality$image();
            var image=((SnowSpritePixels)copied.contents()).naturality$image();
            int width=source.contents().width(),height=source.contents().height();
            for(int y=0;y<height;y++)for(int x=0;x<width;x++)
                check(image.getPixel(x,y)==sourceImage.getPixel(x,y),"Near half retains exact source pixels including alpha");
            // Fade inputs retain the original texture coordinates rather than
            // collapsing the whole quad to its average before the shader runs.
            for(int i=0;i<4;i++) {
                float au=(Float.intBitsToFloat(a.get(4+i*7))-copied.getU0())/(copied.getU1()-copied.getU0());
                float bu=(Float.intBitsToFloat(b.get(4+i*7))-source.getU0())/(source.getU1()-source.getU0());
                check(Math.abs(au-bu)<.0001,"Texture coordinate mapping retains original detail");
            }
        }
    }
    private static BlockStateModel unwrap(BlockStateModel model) {
        try {
            var field = WrapperBlockStateModel.class.getDeclaredField("wrapped"); field.setAccessible(true);
            return (BlockStateModel)field.get(model);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static int countCube(Minecraft client) {
        int count = 0;
        for (var pos : BlockPos.betweenClosed(0, 100, 0, 2, 102, 2)) {
            var state = client.level.getBlockState(pos);
            count += collect(client, pos, state, client.getModelManager().getBlockStateModelSet().get(state)).size();
        }
        return count;
    }
    private static List<List<Integer>> collect(Minecraft client, BlockPos pos, BlockState state, BlockStateModel model) {
        var result = new ArrayList<List<Integer>>();
        var emitter = Renderer.get().quadEmitter(q -> {
            var values = new ArrayList<Integer>(); values.add(q.tintIndex());
            for (int i = 0; i < 4; i++) {
                values.add(Float.floatToIntBits(q.x(i))); values.add(Float.floatToIntBits(q.y(i))); values.add(Float.floatToIntBits(q.z(i)));
                values.add(Float.floatToIntBits(q.u(i))); values.add(Float.floatToIntBits(q.v(i))); values.add(q.color(i)); values.add(q.lightmap(i));
            }
            result.add(values);
        });
        model.emitQuads(emitter, client.level, pos, state, RandomSource.create(0), _ -> false);
        return result;
    }
}
