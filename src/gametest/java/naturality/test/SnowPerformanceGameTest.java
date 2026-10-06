package naturality.test;

import java.lang.management.ManagementFactory;
import naturality.snow.SnowGeometry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.util.RandomSource;

/** Fixed input CPU/allocation probes; GPU submission and FPS are deliberately excluded. */
public final class SnowPerformanceGameTest implements FabricClientGameTest {
    private static volatile double sink;
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                for (var support : new net.minecraft.world.level.block.state.BlockState[]{
                        Blocks.STONE.defaultBlockState(), Blocks.STONE_SLAB.defaultBlockState(),
                        Blocks.OAK_STAIRS.defaultBlockState(), Blocks.OAK_FENCE.defaultBlockState(),
                        Blocks.OAK_LEAVES.defaultBlockState()}) {
                    var snow = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3);
                    var view = (BlockAndTintGetter) java.lang.reflect.Proxy.newProxyInstance(
                        BlockAndTintGetter.class.getClassLoader(), new Class<?>[]{BlockAndTintGetter.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getBlockState")) {
                                var p = (BlockPos) args[0];
                                if (p.getX() != 0 || p.getZ() != 0) return Blocks.AIR.defaultBlockState();
                                return p.getY() == 1 ? snow : p.getY() == 0 ? support
                                    : p.getY() == -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                            }
                            if (method.getName().equals("getBrightness")) return 15;
                            return method.invoke(client.level, args);
                        });
                    String name = support.getBlock().toString();
                    probe(name + " shape", () -> sink = SnowGeometry.shape(view, BlockPos.ZERO.above(), 3).bounds().maxY);
                    var model = client.getModelManager().getBlockStateModelSet().get(support);
                    int[] quads = {0}; double[] area = {0};
                    var emitter = net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().quadEmitter(q -> {
                        quads[0]++;
                        double ax=q.x(1)-q.x(0), ay=q.y(1)-q.y(0), az=q.z(1)-q.z(0);
                        double bx=q.x(3)-q.x(0), by=q.y(3)-q.y(0), bz=q.z(3)-q.z(0);
                        area[0] += Math.sqrt(Math.pow(ay*bz-az*by,2)+Math.pow(az*bx-ax*bz,2)+Math.pow(ax*by-ay*bx,2));
                    });
                    var random=RandomSource.create(0);
                    probe(name + " overlay", () -> {
                        quads[0]=0; area[0]=0;
                        model.emitQuads(emitter,view,BlockPos.ZERO,support,random,direction -> false);
                    });
                    System.out.println("SNOW_MESH " + name + " quads=" + quads[0] + " area=" + area[0]);
                }
            });
        }
    }
    private static void probe(String name, Runnable work) {
        for (int i=0;i<100;i++) work.run();
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        long thread=Thread.currentThread().threadId();
        long bytes=bean.getThreadAllocatedBytes(thread), start=System.nanoTime();
        for (int i=0;i<200;i++) work.run();
        long elapsed=System.nanoTime()-start, allocated=bean.getThreadAllocatedBytes(thread)-bytes;
        System.out.println("SNOW_PROBE " + name + " ns/op=" + elapsed/200 + " bytes/op=" + allocated/200);
    }
}
