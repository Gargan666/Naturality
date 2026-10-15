package naturality.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import naturality.client.render.RenderDetail;
import naturality.config.NaturalityConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.phys.Vec3;

/** Normal generated forest/snow terrain, render-mesh counts and a chunk-travel CPU profile.
 * Gametest advances simulation in lockstep, so its wall time is never reported as gameplay FPS. */
public final class TerrainLodWorldGameTest implements FabricClientGameTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void view(Minecraft client, Vec3 eye) { RenderDetail.view(null, Vec3.ZERO); RenderDetail.view(client.level, eye); }
    private static int quads(Minecraft client, BlockPos pos, BlockState state) {
        int[] count = {0};
        var emitter = Renderer.get().quadEmitter(_ -> count[0]++);
        client.getModelManager().getBlockStateModelSet().get(state).emitQuads(emitter, TestFixtures.level(client), pos, state,
            RandomSource.create(state.getSeed(pos)), _ -> false);
        return count[0];
    }
    private static void details(Minecraft client) {
        var config = NaturalityConfig.get().effects;
        var pos = new BlockPos(60,150,-300);
        for (var block : new Block[]{Blocks.SHORT_GRASS, Blocks.FERN, Blocks.DANDELION, Blocks.POPPY, Blocks.PINK_PETALS,
                Blocks.TALL_GRASS, Blocks.LARGE_FERN, Blocks.SUNFLOWER, Blocks.SHORT_DRY_GRASS, Blocks.TALL_DRY_GRASS}) {
            var state = block.defaultBlockState();
            view(client, Vec3.atCenterOf(pos));
            int near = quads(client,pos,state);
            check(near > 0, "Nearby decorative plant remains: " + block);
            view(client, Vec3.atCenterOf(pos).add(0,0,-220));
            check(quads(client,pos,state) == 0, "Distant decorative plant emits no geometry: " + block);
            view(client, Vec3.atCenterOf(pos));
            check(quads(client,pos,state) == near, "Approaching restores plant geometry: " + block);
        }
        var lower = new BlockPos(60,159,-300);
        var upper = lower.above();
        view(client, new Vec3(60,-40,-300)); // Lower section is 184 blocks away, upper section is 200.
        check(quads(client,lower,Blocks.TALL_GRASS.defaultBlockState()) > 0
            && quads(client,upper,Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF,DoubleBlockHalf.UPPER)) > 0,
            "Tall plants do not lose one half at vertical section boundaries");
        view(client, Vec3.atCenterOf(pos).add(0,0,-220));
        check(quads(client,pos,Blocks.WHEAT.defaultBlockState()) > 0, "Crops remain rendered");
        config.distantDetails = false;
        check(quads(client,pos,Blocks.POPPY.defaultBlockState()) > 0, "Small-detail toggle restores distant flowers");
        config.distantDetails = true;

        var support = Blocks.STONE.defaultBlockState();
        var original = TestFixtures.level(client).getBlockState(pos); var above = TestFixtures.level(client).getBlockState(pos.above());
        try {
            TestFixtures.level(client).setBlock(pos,support,2); TestFixtures.level(client).setBlock(pos.above(),Blocks.SNOW.defaultBlockState(),2);
            var model = client.getModelManager().getBlockStateModelSet().get(support);
            view(client,Vec3.atCenterOf(pos));
            check(model.createGeometryKey(TestFixtures.level(client),pos,support,RandomSource.create(0)) == null,
                "Nearby snow overlay retains position-dependent geometry");
            view(client,Vec3.atCenterOf(pos).add(0,0,-220));
            check(quads(client,pos.above(),Blocks.SNOW.defaultBlockState()) > 0, "Supported snow layer geometry remains rendered");
            check(model.createGeometryKey(TestFixtures.level(client),pos,support,RandomSource.create(0)) != null,
                "Distant snow overlay bypasses position-dependent work");
            config.distantDetails = false;
            check(model.createGeometryKey(TestFixtures.level(client),pos,support,RandomSource.create(0)) == null,
                "Disabling detail LOD restores snow overlays");
        } finally {
            config.distantDetails = true;
            TestFixtures.level(client).setBlock(pos,original,2); TestFixtures.level(client).setBlock(pos.above(),above,2);
            view(client,client.gameRenderer.mainCamera().position());
        }
    }
    private static void select(Minecraft client, boolean enabled) {
        var config = NaturalityConfig.get().effects;
        config.distantDetails = config.distantTextures = enabled;
        view(client,client.gameRenderer.mainCamera().position());
        // Use the normal reload entry point; resetting only the renderer leaves
        // the extraction thread's dirty-section tracker pointing at old meshes.
        client.levelExtractor.allChanged();
    }
    private static long meshes(Minecraft client, String label, StringBuilder report) {
        long indices = 0;
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
            for (var section : client.levelRenderer.visibleSections()) for (var layer : ChunkSectionLayer.values()) {
                var draw = section.getSectionMesh().getSectionDraw(layer);
                if (draw != null) indices += draw.indexCount();
            }
        }
        String line = String.format(Locale.ROOT,"%s visible_mesh_indices=%d %s%n",label,indices,client.levelExtractor.sectionStatistics());
        report.append(line); System.out.print(line);
        return indices;
    }
    private static void downloaded(ClientGameTestContext context) {
        // This Minecraft version sends a rounded chunk view, while Fabric's
        // generic download wait still expects every corner of a square.
        context.waitFor(client -> {
            var center=TestFixtures.player(client).chunkPosition();
            int radius=client.options.getEffectiveRenderDistance();
            for (int z=-radius-1;z<=radius+1;z++) for (int x=-radius-1;x<=radius+1;x++) {
                int cx=center.x()+x, cz=center.z()+z;
                if (net.minecraft.server.level.ChunkTrackingView.isWithinDistance(center.x(),center.z(),radius,cx,cz,true)
                    && TestFixtures.level(client).getChunk(cx,cz,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)==null) return false;
            }
            return true;
        },6000);
    }
    @Override public void runTest(ClientGameTestContext context) {
        var effects = NaturalityConfig.get().effects;
        boolean textures=effects.distantTextures, detail=effects.distantDetails;
        int distance=TestFixtures.require(context.computeOnClient(c -> c.options.renderDistance().get()), "context.computeOnClient(c -> c.options.renderDistance().get())");
        String renderer=net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium") ? "sodium" : "fabric";
        var report=new StringBuilder("Normal overworld; seed=84219871; distance=32; wind/leaf LOD unchanged.\n"
            + "Mesh counts and CPU profile only; lockstep gametest timing is not gameplay FPS.\n");
        Path directory=Path.of("profiling");
        try (var recording=new Recording(Configuration.getConfiguration("profile"))) {
            Files.createDirectories(directory);
            recording.start();
            try (var world=context.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> s.setSeed("84219871")).create()) {
                effects.distantTextures=effects.distantDetails=true;
                var server=world.getServer();
                server.runOnServer(s -> {
                    check(s.overworld().getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator,"Performance world must use normal terrain generation");
                    s.setWeatherParameters(12000,0,false,false);
                });
                server.runCommand("gamemode spectator @a");
                server.runCommand("time set 6000");
                int[] forestHeight={111};
                server.runOnServer(s -> forestHeight[0]=Math.max(111,s.overworld().getHeight(Heightmap.Types.WORLD_SURFACE,52,-300)+35));
                report.append("forest_camera=52,").append(forestHeight[0]).append(",-300\n");
                server.runCommand("tp @a 52 "+forestHeight[0]+" -300 90 10");
                context.waitTicks(30);
                context.runOnClient(c -> check(TestFixtures.level(c).getBlockState(BlockPos.containing(c.gameRenderer.mainCamera().position())).isAir(),
                    "Forest camera must be above terrain"));
                context.runOnClient(TerrainLodWorldGameTest::details);
                context.runOnClient(c -> { c.options.renderDistance().set(32); c.options.broadcastOptions(); });
                context.waitTicks(360);
                downloaded(context);
                world.getConnection().waitForChunksRender(false,6000);
                // An alternating A/B/A sequence keeps generation/JIT warm-up out of the mesh comparison.
                for (int pass=0;pass<3;pass++) {
                    boolean enabled=pass!=1;
                    context.runOnClient(c -> select(c,enabled));
                    context.waitTicks(100);
                    downloaded(context);
                    world.getConnection().waitForChunksRender(false,6000);
                    context.waitTicks(80);
                    String label="forest-"+pass+"-lod-"+enabled;
                    context.takeScreenshot("lod-world-"+label);
                    context.runOnClient(c -> meshes(c,label,report));
                }
                int[] travelHeight={111};
                server.runOnServer(s -> travelHeight[0]=Math.max(111,s.overworld().getHeight(Heightmap.Types.WORLD_SURFACE,340,-300)+35));
                server.runCommand("tp @a 340 "+travelHeight[0]+" -300 90 10");
                context.waitTicks(80);
                context.takeScreenshot("lod-world-travel-arriving");
                context.runOnClient(c -> meshes(c,"travel-arriving",report));
                context.waitTicks(280);
                downloaded(context);
                world.getConnection().waitForChunksRender(false,6000);
                context.takeScreenshot("lod-world-travel-settled");
                context.runOnClient(c -> meshes(c,"travel-settled",report));
                BlockPos[] snow={null};
                server.runOnServer(s -> {
                    var found=s.overworld().findClosestBiome3d(b -> b.is(Biomes.SNOWY_TAIGA) || b.is(Biomes.SNOWY_PLAINS),
                        new BlockPos(52,80,-300),4096,64,64);
                    check(found!=null,"Seed must provide a natural snowy biome");
                    var p=TestFixtures.require(found, "found").getFirst();
                    int y=s.overworld().getHeight(Heightmap.Types.WORLD_SURFACE,p.getX(),p.getZ());
                    snow[0]=new BlockPos(p.getX(),y+40,p.getZ());
                    report.append("snow_camera=").append(snow[0]).append('\n');
                });
                double snowYaw=Math.toDegrees(Math.atan2(-(snow[0].getX()-52),snow[0].getZ()+300));
                server.runCommand("tp @a "+snow[0].getX()+" "+snow[0].getY()+" "+snow[0].getZ()+" "+snowYaw+" 12");
                context.waitTicks(360);
                downloaded(context);
                world.getConnection().waitForChunksRender(false,6000);
                context.takeScreenshot("lod-world-snow");
                context.runOnClient(c -> meshes(c,"snow-settled",report));
            }
            recording.stop();
            recording.dump(directory.resolve("terrain-lod-world-"+renderer+".jfr"));
            Files.writeString(directory.resolve("terrain-lod-world-"+renderer+".txt"),report);
        } catch (Exception e) { throw new AssertionError(e); }
        finally {
            effects.distantTextures=textures; effects.distantDetails=detail;
            context.runOnClient(c -> c.options.renderDistance().set(distance));
        }
    }
}
