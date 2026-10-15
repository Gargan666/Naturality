package naturality.client.menu;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.nio.ByteOrder;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.joml.Vector4f;

/** Horizontal fog and exact surface boundaries for the disposable menu model. */
public final class MenuFog {
    public static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withUniform("MenuRegion", UniformType.UNIFORM_BUFFER)
            .withUniform("MenuFloorsLow", UniformType.UNIFORM_BUFFER)
            .withUniform("MenuFloorsHigh", UniformType.UNIFORM_BUFFER).build();
    private static final int SIZE = 240, HALF = SIZE * SIZE / 2;
    private static final WeakHashMap<LevelChunk, int[]> floors = new WeakHashMap<>();
    private static final int[] surface = new int[SIZE * SIZE];
    private static MappableRingBuffer buffer, low, high;
    private static ClientLevel sampledLevel;
    private static boolean dirty = true;
    private static int originX, originZ;

    public static void invalidate(LevelChunk chunk) { floors.remove(chunk); dirty = true; }
    public static int surfaceFloor(LevelChunk chunk, int x, int z) { return floors(chunk)[(z & 15) * 16 + (x & 15)]; }

    private static int[] floors(LevelChunk chunk) {
        return floors.computeIfAbsent(chunk, c -> {
            int[] result = new int[256];
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                result[z * 16 + x] = MenuSurface.surface(c, x, z);
            return result;
        });
    }

    public static void prepare() {
        if (buffer == null) {
            buffer = new MappableRingBuffer(() -> "Menu region fog", 130, 64);
            // Two packed height buffers stay below 64 KiB each and consume no
            // texture units in terrain's already full transparency pipeline.
            low = new MappableRingBuffer(() -> "Menu lower surface rows", 130, HALF * 2);
            high = new MappableRingBuffer(() -> "Menu upper surface rows", 130, HALF * 2);
        }
        buffer.rotate();
        var client = Minecraft.getInstance();
        boolean active = MenuPreview.active();
        var center = MenuPreview.spawn();
        var camera = client.gameRenderer.mainCamera().position();
        float radius = MenuPreview.radius();
        var level = client.level;
        active = active && level != null;
        int minY = level != null && active ? level.getMinY() : 0;
        if (active && level != null) {
            int x0 = center.getX() - SIZE / 2, z0 = center.getZ() - SIZE / 2;
            if (level != sampledLevel || x0 != originX || z0 != originZ) {
                sampledLevel = level; originX = x0; originZ = z0;
                floors.clear(); dirty = true;
            }
            if (dirty) {
                for (int z = 0; z < SIZE; z++) for (int x = 0; x < SIZE; x++) {
                    int wx = originX + x, wz = originZ + z;
                    var chunk = level.getChunkSource().getChunk(wx >> 4, wz >> 4, false);
                    int floor = chunk == null ? level.getMaxY() + 1 : floors(chunk)[(wz & 15) * 16 + (wx & 15)];
                    surface[z * SIZE + x] = floor - minY;
                }
                low.rotate(); high.rotate();
                try (var a = low.currentBuffer().map(false,true); var b = high.currentBuffer().map(false,true)) {
                    var first = a.data().order(ByteOrder.nativeOrder());
                    var second = b.data().order(ByteOrder.nativeOrder());
                    for(int i=0;i<HALF;i+=2) {
                        first.putInt(surface[i] | surface[i+1] << 16);
                        second.putInt(surface[i+HALF] | surface[i+HALF+1] << 16);
                    }
                }
                dirty = false;
            }
        } else if (sampledLevel != null) {
            sampledLevel = null; floors.clear(); dirty = true;
        }
        double yaw = Math.toRadians(MenuWorld.camera().yaw()), pitch = Math.toRadians(MenuWorld.camera().pitch());
        try (var mapped = buffer.currentBuffer().map(false, true)) {
            Std140Builder.intoBuffer(mapped.data())
                    .putVec4(active ? new Vector4f(center.getX(), center.getZ(), radius * .66F, radius) : new Vector4f(0, 0, 0, 0))
                    .putVec4(active ? new Vector4f((float)camera.x, (float)camera.y, (float)camera.z, MenuSurface.SURFACE_DEPTH) : new Vector4f(0, 0, 0, 0))
                    .putVec4(originX, originZ, minY, SIZE)
                    .putVec4((float)(-Math.sin(yaw) * Math.cos(pitch)), (float)Math.sin(pitch),
                            (float)(Math.cos(yaw) * Math.cos(pitch)), Math.clamp(MenuWorld.camera().pitch() / 8F, 0, 1));
        }
    }
    public static void bind(RenderPass pass) {
        if (buffer == null) prepare();
        pass.setUniform("MenuRegion", buffer.currentBuffer());
        pass.setUniform("MenuFloorsLow", low.currentBuffer());
        pass.setUniform("MenuFloorsHigh", high.currentBuffer());
    }
    public static void close() {
        if (buffer != null) { buffer.close(); buffer = null; }
        if (low != null) { low.close(); low = null; }
        if (high != null) { high.close(); high = null; }
        sampledLevel = null; floors.clear(); dirty = true;
    }
    private MenuFog() { }
}
