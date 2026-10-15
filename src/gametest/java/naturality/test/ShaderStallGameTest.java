package naturality.test;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import naturality.client.fluid.WaterVisuals;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/** Reproduce a cold underwater shader while every terrain worker needs the render thread. */
public final class ShaderStallGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static void coldPipeline(int pass) {
        var pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("naturality", "test/cold_water_finish_" + pass))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath("naturality", "core/water_finish"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
            .withBindGroupLayout(WaterVisuals.LAYOUT).withBindGroupLayout(WaterVisuals.SCENE_LAYOUT)
            .withDepthStencilState(Optional.empty())
            .withColorTargetState(com.mojang.renderpearl.api.pipeline.ColorTargetState.DEFAULT).build();
        var executor = Util.backgroundExecutor();
        check(executor.service() instanceof ForkJoinPool, "Regression requires the terrain ForkJoinPool");
        int workers = ((ForkJoinPool)executor.service()).getParallelism();
        var occupied = new CountDownLatch(workers);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(workers);
        var timedOut = new AtomicBoolean();
        // A failing regression releases its own workers instead of hanging the test JVM.
        var watchdog = Thread.ofPlatform().daemon().name("Shader stall test watchdog").start(() -> {
            try {
                if (!release.await(10, TimeUnit.SECONDS)) { timedOut.set(true); release.countDown(); }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        try {
            for (int i = 0; i < workers; i++) executor.execute(() -> {
                occupied.countDown();
                try { release.await(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { finished.countDown(); }
            });
            check(occupied.await(5, TimeUnit.SECONDS), "All terrain workers must be occupied");
            var queued = CompletableFuture.runAsync(() -> {}, executor);
            long start = System.nanoTime();
            var compiled = RenderSystem.getCompiledPipeline(pipeline);
            long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            check(compiled != null, "Underwater pipeline compiles successfully");
            check(!timedOut.get() && !queued.isDone(), "Cold shader waited for the saturated terrain pool (" + millis + " ms)");
            check(RenderSystem.getCompiledPipeline(pipeline) == compiled, "Warm lookup reuses the compiled pipeline");
            System.out.println("SHADER_STALL pass=" + pass + " occupied_workers=" + workers + " cold_compile_ms=" + millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new AssertionError(e);
        } finally {
            release.countDown(); watchdog.interrupt();
            try { check(finished.await(5, TimeUnit.SECONDS), "Test terrain workers are released"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
        }
    }

    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(_ -> coldPipeline(0));
        var reload = context.computeOnClient(c -> c.reloadResourcePacks());
        context.waitFor(_ -> reload.isDone());
        reload.join();
        context.runOnClient(_ -> coldPipeline(1));
        int previous = TestFixtures.require(context.computeOnClient(c -> c.options.renderDistance().get()), "context.computeOnClient(c -> c.options.renderDistance().get())");
        try (var world = context.worldBuilder().setUseConsistentSettings(false)
                .adjustSettings(s -> s.setSeed("84219871")).create()) {
            var server = world.getServer();
            server.runOnServer(s -> check(s.overworld().getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator,
                "Use a normal generated world"));
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set 6000");
            context.runOnClient(c -> { c.options.renderDistance().set(32); c.options.broadcastOptions(); });
            server.runCommand("tp @a 52 111 -300 90 10");
            context.waitTicks(120);
            BlockPos[] water = {null};
            server.runOnServer(s -> {
                var level = s.overworld();
                for (int z = -380; z <= -140 && water[0] == null; z += 8) for (int x = -60; x <= 180; x += 8) {
                    int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 2;
                    var pos = new BlockPos(x, y, z);
                    if (level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos.above()).is(FluidTags.WATER)) {
                        water[0] = pos; break;
                    }
                }
                check(water[0] != null, "Seed must provide natural water to enter");
            });
            var p = water[0];
            for (int i = 0; i < 3; i++) {
                server.runCommand("tp @a " + (p.getX() + .5) + " " + (p.getY() - 1) + " " + (p.getZ() + .5) + " 90 12");
                context.waitTicks(50);
                context.runOnClient(c -> check(c.gameRenderer.mainCamera().getFluidInCamera() == net.minecraft.world.level.material.FogType.WATER,
                    "Camera must enter natural water"));
                context.takeScreenshot("shader-stall-underwater-" + i);
                server.runCommand("tp @a " + (52 + 128 * i) + " 111 -300 90 10");
                context.waitTicks(70);
            }
            context.runOnClient(c -> System.out.println("SHADER_STALL natural_world_finished " + c.levelExtractor.sectionStatistics()));
        } finally { context.runOnClient(c -> c.options.renderDistance().set(previous)); }
    }
}
