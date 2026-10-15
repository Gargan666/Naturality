package naturality.test;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.*;
import naturality.Naturality;
import naturality.client.rendering.lighting.dynamic.DynamicLighting.BellLightingModel;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

/** Actual bell geometry/tags plus a GPU oracle for its stretched texture grid. */
public final class BellLightingGameTest implements FabricClientGameTest {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Naturality.id("pipeline/bell_lighting_grid_test"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Naturality.id("core/bell_lighting_grid_test"))
            .withDepthStencilState(Optional.empty()).withColorTargetState(ColorTargetState.DEFAULT).build());

    @Override public void runTest(ClientGameTestContext context) {
        try (var world = TestWorlds.disposable(context).create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set noon");
            server.runCommand("fill -4 100 -4 4 100 4 smooth_quartz");
            server.runCommand("fill -3 103 -2 -1 103 2 smooth_quartz");
            server.runCommand("setblock 0 101 0 bell[attachment=floor,facing=north]");
            server.runCommand("tp @a 2.2 99.8 0.8 100 -4");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(12);
            context.runOnClient(c -> {
                verifyGridGpu();
                for (var state : net.minecraft.world.level.block.Blocks.BELL.getStateDefinition().getPossibleStates())
                    checkModel(c, state);
            });
            for (boolean oit : new boolean[]{false, true}) {
                boolean[] original = {false};
                context.runOnClient(c -> { original[0] = c.options.improvedTransparency().get(); c.options.improvedTransparency().set(oit); });
                try {
                    world.getConnection().waitForChunksRender();
                    context.waitTicks(5);
                    context.takeScreenshot("bell-lighting-post-" + oit);
                } finally { context.runOnClient(c -> c.options.improvedTransparency().set(original[0])); }
            }
            server.runCommand("tp @a 1.8 99.8 -1.0 55 -4");
            world.getConnection().waitForClientboundPackets(); context.waitTicks(4);
            context.takeScreenshot("bell-lighting-oblique");
        }
    }

    private static void checkModel(Minecraft client, net.minecraft.world.level.block.state.BlockState state) {
        var model = client.getModelManager().getBlockStateModelSet().get(state);
        if (!(model instanceof BellLightingModel)) throw new AssertionError("Bell lighting wrapper missing: " + state);
        BlockStateModel original;
        try {
            var field = WrapperBlockStateModel.class.getDeclaredField("wrapped"); field.setAccessible(true);
            original = (BlockStateModel)field.get(model);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        var pos = new BlockPos(0, 101, 0);
        var baseKey = original.createGeometryKey(TestFixtures.level(client), pos, state, RandomSource.create(0));
        var markedKey = model.createGeometryKey(TestFixtures.level(client), pos, state, RandomSource.create(0));
        if (baseKey != null && (markedKey == null || markedKey.equals(baseKey)))
            throw new AssertionError("Bell tagged geometry must have its own cache key");
        var marked = collect(client, state, model, true);
        var baseline = collect(client, state, original, false);
        if (marked.isEmpty() || !marked.equals(baseline))
            throw new AssertionError("Bell geometry/UV/tint changed: " + state);
    }

    private static List<List<Integer>> collect(Minecraft client, net.minecraft.world.level.block.state.BlockState state,
            BlockStateModel model, boolean marked) {
        var output = new ArrayList<List<Integer>>();
        var emitter = Renderer.get().quadEmitter(q -> {
            var values = new ArrayList<Integer>();
            for (int i = 0; i < 4; i++) {
                if (marked && q.color(i) >>> 24 != BellLightingModel.TEXEL_LIGHTING_TAG)
                    throw new AssertionError("Bell opaque face lost lighting marker");
                values.add(Float.floatToIntBits(q.x(i))); values.add(Float.floatToIntBits(q.y(i)));
                values.add(Float.floatToIntBits(q.z(i))); values.add(Float.floatToIntBits(q.u(i)));
                values.add(Float.floatToIntBits(q.v(i))); values.add(q.color(i) & 0xFFFFFF);
            }
            output.add(values);
        });
        model.emitQuads(emitter, TestFixtures.level(client), new BlockPos(0, 101, 0), state, RandomSource.create(0), _ -> false);
        return output;
    }

    private static void verifyGridGpu() {
        int width = 73, height = 257;
        var target = new TextureTarget("Bell lighting grid oracle", width, height, GpuFormat.RGBA8_UNORM, null);
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        try (var buffer = RenderSystem.getDevice().createBuffer(() -> "Bell lighting readback", 9, width * height * 4L)) {
            try (var pass = encoder.createRenderPass(() -> "Bell stretched UV lighting", TestFixtures.require(target.getColorTextureView(), "target.getColorTextureView()"), Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE)); pass.draw(3, 1, 0, 0);
            }
            encoder.copyTextureToBuffer(TestFixtures.require(target.getColorTexture(), "target.getColorTexture()"), buffer, 0, () -> {}, 0);
            try (var fence = encoder.createFence()) { encoder.submit(); fence.awaitCompletion(-1); }
            try (var mapped = buffer.map(true, false)) {
                int corrected = 0;
                for (int y = 0; y < height; y++) {
                    double v = (y + .5) / height;
                    double center = (Math.floor(65.2 + v * 15) + .5 - 65.2) / 15;
                    int expected = (int)Math.round((.2 + .6 * center) * 255);
                    int legacy = (int)Math.round((.2 + .6 * (Math.floor(v * 16) + .5) / 16) * 255);
                    if (expected != legacy) corrected++;
                    for (int x = 0; x < width; x++) {
                        int actual = mapped.data().getInt((x + y * width) * 4) & 255;
                        if (Math.abs(actual - expected) > 1)
                            throw new AssertionError("Lighting did not follow bell texel row at " + x + "," + y + ": " + actual + "/" + expected);
                    }
                }
                if (corrected < height / 2) throw new AssertionError("Fixture must expose the original grid mismatch");
                System.out.println("Bell texture-grid GPU oracle passed: " + width * height + " pixels");
            }
        } finally { target.destroyBuffers(); }
    }
}
