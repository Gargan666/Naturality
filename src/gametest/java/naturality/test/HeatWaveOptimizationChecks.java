package naturality.test;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import java.lang.management.ManagementFactory;
import java.util.*;
import naturality.client.fluid.HeatWaves;
import naturality.config.NaturalityConfig;
import naturality.test.reference.ReferenceHeatWaves;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/** Compare the real GPU output and source selection with the frozen original. */
final class HeatWaveOptimizationChecks {
    private static final Class<?> ORIGINAL = ReferenceHeatWaves.class, CURRENT = HeatWaves.class;

    static void verify(Minecraft client, String stage, boolean benchmark) {
        scan(ORIGINAL, client);
        scan(CURRENT, client);
        require(field(ORIGINAL, "boxes").equals(field(CURRENT, "boxes")), "Heat source/order changed: " + stage);
        require(field(ORIGINAL, "fireBoxes").equals(field(CURRENT, "fireBoxes")), "Heat source type changed: " + stage);
        if (benchmark) {
            cpuBenchmark("original-" + stage, () -> scan(ORIGINAL, client));
            cpuBenchmark("optimized-" + stage, () -> scan(CURRENT, client));
        }
        var view = client.gameRenderer.mainCamera().getViewRotationMatrix(new Matrix4f());
        comparePixels(stage, 512, 320, view, 70, 0, false);
        comparePixels(stage + "-occluded", 257, 173, view, 110, 1, false);
        comparePixels(stage + "-depth-clipped", 513, 321, view, 90, .005, true);
        comparePixels(stage + "-turned", 512, 320, new Matrix4f(view).rotateY(1.1F), 40, 0, false);
        comparePixels(stage + "-behind", 512, 320, new Matrix4f(view).rotateY((float)Math.PI), 70, 0, false);
        if (benchmark) gpuBenchmark(stage, view);
        System.out.println("Heat equivalence passed: " + stage);
    }

    static void synthetic(Minecraft client) {
        var p = client.gameRenderer.mainCamera().position();
        for (int count : new int[]{1, 64}) {
            var volumes = new ArrayList<AABB>();
            var fires = new HashSet<AABB>();
            for (int i = 0; i < count; i++) {
                boolean fire = (i & 1) == 0;
                double x = count == 1 ? p.x - .5 : p.x - 12 + (i % 8) * 3;
                double y = p.y - 3, z = p.z - 6 - (i / 8) * 3;
                var box = new AABB(x, y, z, x + (fire ? 1 : 5), y + (fire ? 2 : 5), z + (fire ? 1 : 5));
                volumes.add(box);
                if (fire) fires.add(box);
            }
            install(client, volumes, fires);
            comparePixels("synthetic-" + count, 513, 321, new Matrix4f(), 90, 0, false);
            comparePixels("synthetic-edge-" + count, 513, 321, new Matrix4f().rotateY(.65F), 40, 0, true);
            gpuBenchmark("synthetic-" + count, new Matrix4f());
        }
        // A volume surrounding the eye must fall back to full-screen coverage.
        install(client, List.of(new AABB(p.x-2,p.y-2,p.z-2,p.x+3,p.y+3,p.z+3)), Set.of());
        comparePixels("camera-inside", 513, 321, new Matrix4f().rotateX(.7F), 110, 0, true);
        // Fragmented sources cross positive/negative chunk and Y-section bounds,
        // with far more than 64 candidates. Compare both selection and merge order.
        var random = new Random(412781);
        var sources = new net.minecraft.world.level.block.Block[]{
            net.minecraft.world.level.block.Blocks.LAVA,
            net.minecraft.world.level.block.Blocks.FIRE,
            net.minecraft.world.level.block.Blocks.SOUL_FIRE,
            net.minecraft.world.level.block.Blocks.STONE};
        for (int y : new int[]{96, 112, 128}) for (int z = -24; z <= 24; z += 3) for (int x = -24; x <= 24; x += 2)
            client.level.setBlock(new BlockPos(x, y, z), sources[random.nextInt(sources.length)].defaultBlockState(), 2);
        scan(ORIGINAL, client);
        scan(CURRENT, client);
        require(((List<?>)field(CURRENT, "boxes")).size() == 64, "Dense fixture must reach source limit");
        verify(client, "fragmented", true);
    }

    @SuppressWarnings("unchecked")
    private static void install(Minecraft client, List<AABB> volumes, Set<AABB> fires) {
        for (var type : List.of(ORIGINAL, CURRENT)) {
            var list = (List<AABB>)field(type, "boxes"); list.clear(); list.addAll(volumes);
            var set = (Set<AABB>)field(type, "fireBoxes"); set.clear(); set.addAll(fires);
            set(type, "world", client.level);
            set(type, "scanned", client.level.getGameTime());
            set(type, "origin", BlockPos.containing(client.gameRenderer.mainCamera().position()));
        }
    }

    private static void comparePixels(String label, int width, int height, Matrix4f view, float fov, double depth, boolean skew) {
        var original = target(width, height);
        var current = target(width, height);
        RenderSystem.backupProjectionMatrix();
        var stack = RenderSystem.getModelViewStack(); stack.pushMatrix(); stack.set(view);
        try (var projection = new ProjectionMatrixBuffer("Heat equivalence"); var pattern = pattern(width, height)) {
            var matrix = projection(width, height, fov);
            if (skew) matrix.rotateZ(.17F).scale(.94F, 1, 1).translate(.03F, .02F, 0);
            RenderSystem.setProjectionMatrix(projection.getBuffer(matrix), ProjectionType.PERSPECTIVE);
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToTexture(original.getColorTexture(), pattern);
            current.copyColorFrom(original);
            encoder.clearDepthTexture(original.getDepthTexture(), depth);
            encoder.clearDepthTexture(current.getDepthTexture(), depth);
            ReferenceHeatWaves.render(original);
            HeatWaves.render(current);
            int[] a = read(original), b = read(current);
            int mismatches = 0, affected = 0;
            for (int i = 0; i < a.length; i++) {
                if (a[i] != b[i]) mismatches++;
                if (a[i] != net.minecraft.util.ARGB.toABGR(pattern.getPixel(i % width, i / width))) affected++;
            }
            System.out.printf(Locale.ROOT, "Heat pixels %s: %d/%d differ, %d affected by original%n", label, mismatches, a.length, affected);
            if (Set.of("lava-fire", "lake", "synthetic-1", "synthetic-64", "camera-inside").contains(label))
                require(affected > 0, "Comparison must exercise visible heat: " + label);
            // Nearest sampling can move by one texel at floating-point boundaries
            // when interpolating the bounded quad instead of the oversized triangle.
            require(mismatches <= Math.max(2, a.length / 5000), "Heat image mismatch: " + label + " (" + mismatches + ")");
        } finally {
            original.destroyBuffers(); current.destroyBuffers();
            stack.popMatrix(); RenderSystem.restoreProjectionMatrix();
        }
    }

    private static void gpuBenchmark(String label, Matrix4f view) {
        var target = target(1920, 1080);
        RenderSystem.backupProjectionMatrix();
        var stack = RenderSystem.getModelViewStack(); stack.pushMatrix(); stack.set(view);
        try (var projection = new ProjectionMatrixBuffer("Heat benchmark"); var pattern = pattern(1920, 1080)) {
            RenderSystem.setProjectionMatrix(projection.getBuffer(projection(1920, 1080, 70)), ProjectionType.PERSPECTIVE);
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToTexture(target.getColorTexture(), pattern);
            encoder.clearDepthTexture(target.getDepthTexture(), 0);
            for (int i = 0; i < 8; i++) {
                ReferenceHeatWaves.render(target); HeatWaves.render(target); encoder.submit();
            }
            for (boolean optimized : new boolean[]{false, true}) {
                double[] times = new double[5];
                for (int batch = 0; batch < times.length; batch++) {
                    finish();
                    long start = System.nanoTime();
                    for (int i = 0; i < 12; i++) {
                        if (optimized) HeatWaves.render(target); else ReferenceHeatWaves.render(target);
                        encoder.submit();
                    }
                    finish();
                    times[batch] = (System.nanoTime() - start) / 1e6 / 12;
                }
                Arrays.sort(times);
                System.out.printf(Locale.ROOT, "Heat render benchmark %s %s: %.3f ms/pass (1920x1080, median of 5x12, CPU+GPU)%n",
                    label, optimized ? "optimized" : "original", times[2]);
            }
        } finally {
            target.destroyBuffers(); stack.popMatrix(); RenderSystem.restoreProjectionMatrix();
        }
    }

    private static Matrix4f projection(int width, int height, float fov) {
        var projection = new Projection(); projection.setupPerspective(.05F, 1000, fov, width, height);
        return projection.getMatrix(new Matrix4f());
    }
    private static TextureTarget target(int width, int height) {
        return new TextureTarget("Heat comparison", width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
    }
    private static NativeImage pattern(int width, int height) {
        var image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            image.setPixel(x, y, 0xff000000 | ((x * 17) & 255) << 16 | ((y * 31) & 255) << 8 | ((x * 3 + y * 7) & 255));
        return image;
    }
    private static int[] read(TextureTarget target) {
        int[] pixels = new int[target.width * target.height];
        try (var buffer = RenderSystem.getDevice().createBuffer(() -> "Heat readback", 9, pixels.length * 4L)) {
            RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(target.getColorTexture(), buffer, 0, () -> {}, 0);
            finish();
            try (var mapped = buffer.map(true, false)) { mapped.data().asIntBuffer().get(pixels); }
        }
        return pixels;
    }
    private static void finish() {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        try (var fence = encoder.createFence()) { encoder.submit(); fence.awaitCompletion(-1); }
    }
    private static void scan(Class<?> type, Minecraft client) {
        set(type, "world", null);
        try { var method = type.getDeclaredMethod("scan", Minecraft.class); method.setAccessible(true); method.invoke(null, client); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void cpuBenchmark(String name, Runnable action) {
        for (int i = 0; i < 32; i++) action.run();
        var bean = (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        long id = Thread.currentThread().threadId(), bytes = bean.getThreadAllocatedBytes(id), start = System.nanoTime();
        for (int i = 0; i < 128; i++) action.run();
        System.out.printf(Locale.ROOT, "Heat scan %s: %.3f us/scan, %.0f bytes/scan%n", name,
            (System.nanoTime()-start)/128000.0, (bean.getThreadAllocatedBytes(id)-bytes)/128.0);
    }
    static Object field(Class<?> type, String name) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(null); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void set(Class<?> type, String name, Object value) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); field.set(null, value); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}

