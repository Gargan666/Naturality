package naturality.client.ui.menus.previews;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.server.level.ServerPlayer;
import naturality.client.ui.menus.previews.MenuPreview.MenuWorld;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.nio.ByteOrder;
import java.util.WeakHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.joml.Vector4f;

/** Builds the preview payload directly from exposed columns; buried palettes never reach the client. */
public final class MenuSurface {
    /** A conservative band below the exposed surface protects ledges and foundations. */
    public static final int SURFACE_DEPTH = 24;
    private static final int BORDER = 2, STRIDE = 16 + BORDER * 2;
    public static LevelChunk snapshot(LevelChunk source) {
        return snapshot(source, (x, z) -> source.getLevel().getChunkSource().getChunkForLighting(x, z));
    }
    /** The lighting view includes completed terrain before neighboring chunks become FULL. */
    public static LevelChunk snapshot(LevelChunk source, java.util.function.BiFunction<Integer, Integer, LightChunk> neighbors) {
        var level = source.getLevel();
        var shell = new LevelChunk(level, source.getPos());
        var sections = shell.getSections();
        for (int i = 0; i < sections.length; i++)
            sections[i] = new LevelChunkSection(sections[i].getStates(), source.getSection(i).getBiomes().copy());
        // Empty packets outside the displayed footprint expose a continuous outer
        // row of side faces. Those fully fogged support faces form the skirt.
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && !MenuPreview.containsChunk(source.getPos().x(), source.getPos().z(), serverLevel.getRespawnData().pos()))
            return shell;
        int[] heights = new int[STRIDE * STRIDE];
        int ox = source.getPos().getMinBlockX(), oz = source.getPos().getMinBlockZ();
        for (int z = -BORDER; z < 16 + BORDER; z++) for (int x = -BORDER; x < 16 + BORDER; x++) {
            int wx = ox + x, wz = oz + z;
            var neighbor = x >= 0 && x < 16 && z >= 0 && z < 16
                    ? source : neighbors.apply(wx >> 4, wz >> 4);
            // Never request/generate neighbors merely to trim an edge.
            // An unavailable neighbor must use the adjacent local column, not the opposite edge.
            heights[(z + BORDER) * STRIDE + x + BORDER] = neighbor == null
                    ? surface(source, Math.max(0, Math.min(15, x)), Math.max(0, Math.min(15, z)))
                    : surface(neighbor, wx & 15, wz & 15, wx & ~15, wz & ~15);
        }
        var pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int index = (z + BORDER) * STRIDE + x + BORDER;
            int bottom = heights[index];
            // Include diagonal and recessed cliff feet, not just four immediate neighbors.
            for (int dz = -BORDER; dz <= BORDER; dz++) for (int dx = -BORDER; dx <= BORDER; dx++)
                bottom = Math.min(bottom, heights[index + dz * STRIDE + dx]);
            int highest = source.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            // Retain the visible band plus two hidden support blocks for sealed cut faces.
            bottom = Math.max(source.getMinY(), bottom - SURFACE_DEPTH - 2);
            for (int y = bottom; y <= highest; y++) {
                var state = source.getBlockState(pos.set(ox + x, y, oz + z));
                if (!state.isAir()) sections[shell.getSectionIndex(y)].setBlockState(x, y & 15, z, state, false);
            }
        }
        source.getHeightmaps().forEach(entry -> shell.setHeightmap(entry.getKey(), entry.getValue().getRawData().clone()));
        source.getBlockEntities().forEach((position, entity) -> {
            int index = ((position.getZ() & 15) + BORDER) * STRIDE + (position.getX() & 15) + BORDER;
            if (position.getY() >= heights[index] && !shell.getBlockState(position).isAir())
                shell.getBlockEntities().put(position, entity);
        });
        return shell;
    }
    public static int surface(ChunkAccess chunk, int x, int z) {
        return surface(chunk, x, z, chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ());
    }
    private static int surface(LightChunk chunk, int x, int z, int originX, int originZ) {
        var pos = new BlockPos.MutableBlockPos();
        int wx = originX + x, wz = originZ + z;
        int top = chunk instanceof ChunkAccess access ? access.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) : chunk.getMaxY();
        int first = Integer.MIN_VALUE, run = 0;
        for (int y = top; y >= chunk.getMinY(); y--) {
            // Do not follow a thin floating surface arbitrarily far into deep caves.
            if (first != Integer.MIN_VALUE && y < first - 48) break;
            var state = chunk.getBlockState(pos.set(wx, y, wz));
            // Roofs and thin overhangs are not the ground. Seek a supported surface,
            // preserving everything above it, including the full transparent water column.
            if (state.getFluidState().isEmpty() && !state.isAir() && (state.canOcclude() || run > 0)
                    && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)) {
                if (first == Integer.MIN_VALUE) first = y;
                if (++run == 8) return y + 7;
            } else run = 0;
        }
        return first == Integer.MIN_VALUE ? chunk.getMinY() : first;
    }
    private MenuSurface() { }

    /** Read-only source access: all server locks, migration, simulation and writes target a disposable copy. */
    public static final class MenuSavedRegion {
        public static void copy(Path original, Path destination) throws IOException {
            copy(original, destination, () -> false);
        }
        public static void copy(Path original, Path destination, java.util.function.BooleanSupplier cancelled) throws IOException {
            original = original.toAbsolutePath().normalize();
            var root = NbtIo.readCompressed(original.resolve("level.dat"), NbtAccounter.unlimitedHeap());
            var data = root.getCompoundOrEmpty("Data");
            var player = data.getCompoundOrEmpty("Player");
            var playerFile = original.resolve("playerdata").resolve(Minecraft.getInstance().getUser().getProfileId() + ".dat");
            if (Files.isRegularFile(playerFile)) player = NbtIo.readCompressed(playerFile, NbtAccounter.unlimitedHeap());
            var respawn = ServerPlayer.RespawnConfig.CODEC.parse(NbtOps.INSTANCE, player.getCompoundOrEmpty("respawn"))
                    .result().map(ServerPlayer.RespawnConfig::respawnData)
                    .orElseGet(() -> LevelData.RespawnData.CODEC.parse(NbtOps.INSTANCE, data.getCompoundOrEmpty("spawn"))
                            .result().orElse(LevelData.RespawnData.DEFAULT));
            data.put("spawn", LevelData.RespawnData.CODEC.encodeStart(NbtOps.INSTANCE, respawn).getOrThrow());
            data.remove("Player");
            data.putInt("GameType", 3);
            data.putBoolean("allowCommands", true);
            Files.createDirectories(destination);
            NbtIo.writeCompressed(root, destination.resolve("level.dat"));
            int rx = respawn.pos().getX() >> 9, rz = respawn.pos().getZ() >> 9;
            final Path source = original;
            try (var paths = Files.walk(source)) {
                for (var iterator = paths.filter(Files::isRegularFile).iterator(); iterator.hasNext();) {
                    if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
                    var path = iterator.next();
                    if (Files.isSymbolicLink(path)) continue;
                    var relative = source.relativize(path);
                    String name = path.getFileName().toString();
                    // Vanilla weather and mod event clocks/overrides live in saved data in 26.3.
                    boolean copy = relative.startsWith("datapacks");
                    for (Path part : relative) if (part.toString().equals("data")) copy = true;
                    if (name.matches("r\\.-?\\d+\\.-?\\d+\\.mca") && path.getParent().getFileName().toString().equals("region")) {
                        var pieces = name.split("\\.");
                        copy = Math.abs(Integer.parseInt(pieces[1]) - rx) <= 1 && Math.abs(Integer.parseInt(pieces[2]) - rz) <= 1;
                    }
                    if (copy) {
                        var target = destination.resolve(relative).normalize();
                        if (!target.startsWith(destination.normalize())) throw new IOException("Preview copy outside temporary directory");
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target);
                    }
                }
            }
        }
        private MenuSavedRegion() { }
    }

    /** Horizontal fog and exact surface boundaries for the disposable menu model. */
    public static final class MenuFog {
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
}
