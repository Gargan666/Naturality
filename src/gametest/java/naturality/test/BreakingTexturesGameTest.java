package naturality.test;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import naturality.client.breaking.BreakingTextures;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public final class BreakingTexturesGameTest implements FabricClientGameTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var patterns = new HashSet<Integer>();
            for (int x = -100; x < 100; x++) patterns.add(BreakingTextures.variant(new BlockPos(x, 100, 8)));
            check(patterns.size() == 32, "Positions select all variants, including negative coordinates");
            for (boolean oit : new boolean[]{false, true}) for (int stage = 0; stage < 10; stage++) {
                var path = Identifier.withDefaultNamespace("textures/block/destroy_" + (oit ? "oit_" : "") + "stage_" + stage + ".png");
                try (var stream = client.getResourceManager().open(path); var source = NativeImage.read(stream)) {
                    int[] original = pixels(source);
                    Arrays.sort(original);
                    var unique = new HashSet<Integer>();
                    for (int variant = 0; variant < 32; variant++) {
                        try (var generated = BreakingTextures.generate(source, variant)) {
                            int[] values = pixels(generated);
                            unique.add(Arrays.hashCode(values));
                            Arrays.sort(values);
                            check(Arrays.equals(original, values), "Exact palette, alpha and crack coverage preserved");
                            for (int inset = 0; inset < source.getWidth() / 2; inset++) {
                                int[] before = ring(source, inset);
                                int[] after = ring(generated, inset);
                                Arrays.sort(before);
                                Arrays.sort(after);
                                check(Arrays.equals(before, after), "Every crack pixel stays at its original distance from the center");
                            }
                        }
                    }
                    check(unique.size() >= (stage == 0 ? 4 : 16), "Damage stage has visibly distinct patterns");
                } catch (java.io.IOException exception) { throw new AssertionError(exception); }
            }
            var fallback = ModelBakery.DESTROY_TYPES.get(5);
            BreakingTextures.at(new BlockPos(2, 100, 8), () -> {
                var type = BreakingTextures.current(5, false, fallback);
                check(type != fallback, "Dynamic texture uploaded");
                check(type == BreakingTextures.current(5, false, fallback), "Texture cached between frames");
            });
            check(BreakingTextures.current(5, false, fallback) == fallback, "Submission context restored");
            BreakingTextures.reset();
        });
        boolean[] previous = new boolean[1];
        context.runOnClient(client -> previous[0] = client.options.improvedTransparency().get());
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0 101 3 0 12");
            server.runCommand("time set 6000");
            server.runCommand("fill -5 99 6 5 99 10 stone");
            server.runCommand("fill -3 100 8 0 100 8 stone");
            server.runCommand("setblock 1 100 8 glass");
            server.runCommand("setblock 3 100 8 chest[facing=north]");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(30);
            context.runOnClient(SandParticleChecks::run);
            for (boolean mode : new boolean[]{false, true}) {
                context.runOnClient(client -> client.options.improvedTransparency().set(mode));
                for (int stage : new int[]{0, 4, 9}) {
                    context.runOnClient(client -> {
                        for (int x = -3; x <= 3; x++)
                            client.level.destroyBlockProgress(200 + x, new BlockPos(x, 100, 8), stage);
                    });
                    context.waitTicks(5);
                    context.takeScreenshot("breaking-" + mode + "-" + stage);
                    context.runOnClient(client -> {
                        try {
                            var field = BreakingTextures.class.getDeclaredField("TYPES");
                            field.setAccessible(true);
                            var types = (java.util.Map<?, ?>) field.get(null);
                            for (int x : new int[]{-3, -2, -1, 0, 1, 3}) {
                                int key = (stage + (mode && x == 1 ? 10 : 0)) * 32
                                    + BreakingTextures.variant(new BlockPos(x, 100, 8));
                                check(types.containsKey(key), "Renderer selected position-specific texture at x=" + x + " stage=" + stage + " oit=" + mode + "; cached=" + types.keySet());
                            }
                        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
                    });
                }
            }
            var reload = new AtomicReference<CompletableFuture<Void>>();
            context.runOnClient(client -> reload.set(client.reloadResourcePacks()));
            context.waitFor(client -> reload.get().isDone());
            reload.get().join();
            context.waitTicks(10);
            context.takeScreenshot("breaking-reloaded");
            context.runOnClient(SandParticleChecks::run);
            context.runOnClient(client -> {
                for (int x = -3; x <= 3; x++)
                    client.level.destroyBlockProgress(200 + x, new BlockPos(x, 100, 8), -1);
            });
        } finally {
            context.runOnClient(client -> client.options.improvedTransparency().set(previous[0]));
        }
    }

    private static int[] ring(NativeImage image, int inset) {
        int edge = image.getWidth() - 1 - inset;
        var values = new java.util.ArrayList<Integer>();
        for (int y = inset; y <= edge; y++) for (int x = inset; x <= edge; x++)
            if (x == inset || x == edge || y == inset || y == edge) values.add(image.getPixel(x, y));
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int[] pixels(NativeImage image) {
        int[] values = new int[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            values[y * image.getWidth() + x] = image.getPixel(x, y);
        return values;
    }
}
