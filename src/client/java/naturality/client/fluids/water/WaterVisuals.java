package naturality.client.fluids.water;
import naturality.client.fluids.shared.ProceduralFluids;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import net.minecraft.client.renderer.MappableRingBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import naturality.config.live.NaturalityConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.attribute.EnvironmentAttributes;

/** A bounded, loaded-chunk-only map of exposed water columns, shared by terrain and refraction. */
@org.jspecify.annotations.NullMarked
public final class WaterVisuals {
    public static final int SIZE = 256;
    public static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
        .withUniform("NaturalityWater", UniformType.UNIFORM_BUFFER)
        .withUniform("NaturalityWaterColumns", UniformType.COMBINED_IMAGE_SAMPLER).build();
    public static final BindGroupLayout SCENE_LAYOUT = BindGroupLayout.builder()
        .withUniform("NaturalityWaterMask", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("NaturalityWaterAtmosphere", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("NaturalityWaterOpaqueDepth", UniformType.COMBINED_IMAGE_SAMPLER).build();
    private static @org.jspecify.annotations.Nullable ClientLevel world;
    private static @org.jspecify.annotations.Nullable NativeImage image;
    private static @org.jspecify.annotations.Nullable GpuTexture texture;
    private static @org.jspecify.annotations.Nullable GpuTextureView view;
    private static @org.jspecify.annotations.Nullable MappableRingBuffer uniform;
    private static int originX, originZ, nextTile;
    private static boolean initialized;
    private static final int[] wetTiles = new int[256];
    private static int wetColumns;
    private static boolean besideSubmergedWater;
    private static float frameDepth;
    private static long environmentRevision, depthRevision = -1;
    private static @org.jspecify.annotations.Nullable ClientLevel depthWorld;
    private static @org.jspecify.annotations.Nullable BlockPos depthPosition;
    private static double depthEyeY;
    private static float cachedDepth;
    public static float cameraDepth() { return frameDepth; }

    /** Authoritative fully opaque distance, shared with GLSL through WaterParams.x.
     * This is a color limit, not permission to discard terrain depth. */
    public static float visibilityRange(float renderDistanceBlocks, float murk) {
        float clear = Math.max(12.0F, renderDistanceBlocks * 0.20F);
        float blend = Math.clamp(murk, 0.0F, 1.0F);
        return clear * (1.0F - blend) + 5.0F * blend;
    }
    private static volatile float frameVisibilityRange = Float.POSITIVE_INFINITY;
    public static float visibilityRange() { return frameVisibilityRange; }

    /** Local lens geometry, independent of the entity's breathing/immersion state. */
    public static final class Waterline {
        private static final WaterShaders.@org.jspecify.annotations.Nullable WaterSurface[] surfaces =
            new WaterShaders.WaterSurface[9];
        private static @org.jspecify.annotations.Nullable ClientLevel level;
        private static boolean active;
        private static int x, z;

        public static boolean active() {
            return active && level == Minecraft.getInstance().level && enabled()
                && NaturalityConfig.get().liquids.waterDepth;
        }

        /** Called after the camera position, rotation and projection have all settled. */
        public static void update(net.minecraft.client.Camera camera) {
            active = false;
            java.util.Arrays.fill(surfaces, null);
            level = Minecraft.getInstance().level;
            var current = level;
            if (current == null || !camera.isInitialized() || !ready()
                    || !NaturalityConfig.get().liquids.waterDepth) return;
            var eye = camera.position();
            var plane = camera.getNearPlane(camera.getFov());
            // Include the whole near rectangle, even when looking straight down
            // or rolling the camera. The small margin avoids an eye/mesh-height seam.
            double radius = Math.max(plane.getTopLeft().length(), plane.getBottomRight().length()) + 0.02;
            x = net.minecraft.util.Mth.floor(eye.x) - 1;
            z = net.minecraft.util.Mth.floor(eye.z) - 1;
            int y = net.minecraft.util.Mth.floor(eye.y);
            for (int dz = 0; dz < 3; dz++) for (int dx = 0; dx < 3; dx++) {
                // Only tiles reached by this lens: never scan the world or load chunks.
                if (eye.x + radius < x + dx || eye.x - radius > x + dx + 1
                        || eye.z + radius < z + dz || eye.z - radius > z + dz + 1) continue;
                for (int dy = -1; dy <= 0; dy++) {
                    var surface = WaterShaders.WaterSurface.at(current, new BlockPos(x + dx, y + dy, z + dz));
                    if (surface == null) continue;
                    double low = surface.pos().getY() + Math.min(Math.min(surface.nw(), surface.sw()), Math.min(surface.se(), surface.ne()));
                    double high = surface.pos().getY() + Math.max(Math.max(surface.nw(), surface.sw()), Math.max(surface.se(), surface.ne()));
                    if (eye.y + radius < low || eye.y - radius > high) continue;
                    surfaces[dz * 3 + dx] = surface;
                    active = true;
                    break;
                }
            }
        }

        private static void write(ByteBuffer data, net.minecraft.client.Camera camera) {
            data.putFloat(x - originX).putFloat(z - originZ).putFloat(active() ? 1 : 0).putFloat(0);
            var forward = camera.forwardVector();
            data.putFloat(forward.x()).putFloat(forward.y()).putFloat(forward.z())
                .putFloat(net.minecraft.client.Camera.PROJECTION_Z_NEAR);
            for (var surface : surfaces) {
                if (surface == null) { data.putFloat(0).putFloat(0).putFloat(0).putFloat(0); continue; }
                float base = surface.pos().getY() - (surface.cauldron() ? 0 : 0.001F);
                data.putFloat(base + (float) surface.nw()).putFloat(base + (float) surface.sw())
                    .putFloat(base + (float) surface.se()).putFloat(base + (float) surface.ne());
            }
            for (var surface : surfaces) {
                data.putFloat(surface == null ? 0 : surface.pos().getY())
                    .putFloat(surface != null && surface.cauldron() ? 0.125F : 0)
                    .putFloat(surface == null ? 0 : 1).putFloat(0);
            }
        }
    }


    /** Captured celestial source, independent of optional sun beams and camera flare. */
    public static final class SunTransmission {
        public static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withUniform("NaturalityWaterSun", UniformType.UNIFORM_BUFFER)
            .withUniform("NaturalityWaterSunAtlas", UniformType.COMBINED_IMAGE_SAMPLER).build();
        private static final org.joml.Matrix4f inversePose = new org.joml.Matrix4f();
        private static final org.joml.Vector3f direction = new org.joml.Vector3f();
        private static final ByteBuffer data = ByteBuffer.allocateDirect(96).order(ByteOrder.nativeOrder());
        private static net.minecraft.client.renderer.texture.@org.jspecify.annotations.Nullable TextureAtlas atlas;
        private static net.minecraft.client.renderer.texture.@org.jspecify.annotations.Nullable TextureAtlasSprite sprite;
        private static @org.jspecify.annotations.Nullable MappableRingBuffer buffer;
        private static float brightness;
        public static void beginFrame() { brightness = 0; }
        public static void capture(com.mojang.blaze3d.vertex.PoseStack pose, float rain,
                net.minecraft.client.renderer.texture.TextureAtlas source) {
            inversePose.set(pose.last().pose()).invert();
            pose.last().pose().transformDirection(direction.set(0, 1, 0)).normalize();
            brightness = direction.y > 0 ? rain : 0;
            atlas = source;
            sprite = source.getSprite(net.minecraft.resources.Identifier.withDefaultNamespace("sun"));
        }
        public static void prepare() {
            var client = Minecraft.getInstance();
            var eye = client.gameRenderer.mainCamera().position();
            float visibility = brightness;
            if (visibility > 0) {
                double range = client.options.getEffectiveRenderDistance() * 16.0;
                var end = eye.add(direction.x * range, direction.y * range, direction.z * range);
                visibility *= naturality.client.sky.shared.clouds.CloudLayerRenderer.CloudSunOcclusion.transmission(eye, end);
            }
            data.clear();
            inversePose.get(0, data);
            data.position(64);
            var source = sprite;
            if (source == null) data.putFloat(0).putFloat(0).putFloat(0).putFloat(0);
            else data.putFloat(source.getU0()).putFloat(source.getV0()).putFloat(source.getU1()).putFloat(source.getV1());
            data.putFloat(source == null ? 0 : visibility).putFloat(0).putFloat(0).putFloat(0);
            data.flip();
            var ring = buffer;
            if (ring == null) { ring = new MappableRingBuffer(() -> "Naturality underwater sun", 130, data.capacity()); buffer = ring; }
            else ring.rotate();
            try (var mapped = ring.currentBuffer().map(false, true)) { mapped.data().put(data); }
        }
        public static void bind(RenderPass pass) {
            var uniform = java.util.Objects.requireNonNull(buffer, "Underwater sun prepared");
            pass.setUniform("NaturalityWaterSun", uniform.currentBuffer());
            var source = atlas;
            pass.setUniform("NaturalityWaterSunAtlas", source == null ? view : source.getTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        }
        public static void close() {
            if (buffer != null) { buffer.close(); buffer = null; }
            atlas = null;
            sprite = null;
            brightness = 0;
        }
    }
    /** Shared lightmap policy covers entities, particles and covered terrain alike. */
    private static @org.jspecify.annotations.Nullable ClientLevel lightingWorld;
    private static long lightingTime;
    private static float lightingBlend;

    public static float underwaterLightingBlend() {
        var client = Minecraft.getInstance();
        long now = System.nanoTime();
        if (lightingWorld != client.level || client.level == null) {
            lightingWorld = client.level;
            lightingBlend = 0;
            lightingTime = now;
        }
        float elapsed = lightingTime == 0 ? 0 : Math.min(0.1F, (now - lightingTime) * 1.0e-9F);
        lightingTime = now;
        float target = underwaterSkylightActive() ? 1 : 0;
        if (!client.isPaused()) {
            lightingBlend += Math.clamp(target - lightingBlend, -elapsed, elapsed);
        }
        // A shared lightmap cannot tint only half a frame. During the crossing,
        // leave it neutral and apply the water palette per pixel in the optics pass.
        return Waterline.active() ? 0 : lightingBlend * lightingBlend * (3 - 2 * lightingBlend);
    }

    private static boolean underwaterSkylightActive() {
        return enabled() && NaturalityConfig.get().liquids.waterDepth
            && Minecraft.getInstance().gameRenderer.mainCamera().getFluidInCamera() == FogType.WATER;
    }
    /** Immediate immersion color, independent of the brightness adaptation timer. */
    public static int underwaterLightingTint() {
        var client = Minecraft.getInstance();
        var level = client.level;
        if (level == null || Waterline.active() || !underwaterSkylightActive()) return -1;
        return net.minecraft.client.renderer.BiomeColors.getAverageWaterColor(level, client.gameRenderer.mainCamera().blockPosition());
    }

    public static org.joml.Vector3fc tintLighting(org.joml.Vector3fc color, int tint) {
        if (tint == -1) return color;
        float red = (tint >> 16 & 255) / 255.0F;
        float green = (tint >> 8 & 255) / 255.0F;
        float blue = (tint & 255) / 255.0F;
        float peak = Math.max(1.0F / 255.0F, Math.max(red, Math.max(green, blue)));
        // Preserve the brightest channel and retain material/source colors.
        return new org.joml.Vector3f(color).mul(0.5F + 0.5F * red / peak,
            0.5F + 0.5F * green / peak, 0.5F + 0.5F * blue / peak);
    }
    /** Independent client skylight: water transmits daylight, solid geometry still occludes it. */
    public static final class Skylight {
        private static @org.jspecify.annotations.Nullable ClientLevel level;
        private static volatile net.minecraft.world.level.lighting.@org.jspecify.annotations.Nullable LevelLightEngine engine;
        private static volatile net.minecraft.world.level.lighting.@org.jspecify.annotations.Nullable LayerLightEventListener original;
        private static final java.util.Map<Long, OpticalChunk> chunks = new java.util.concurrent.ConcurrentHashMap<>();
        private static final ThreadLocal<boolean[]> rawLight = ThreadLocal.withInitial(() -> new boolean[1]);
        private static final java.util.Set<BlockPos> changed = new java.util.HashSet<>();
        private static final it.unimi.dsi.fastutil.longs.LongOpenHashSet dirtySections = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

        private static void dirtyColumn(ClientLevel world, int x, int z) {
            // Smooth lighting reads across faces, edges and corners. A column
            // becoming available changes those samples even when its neighbors'
            // own stored light values did not change.
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++)
                for (int y = world.getMinSectionY(); y <= world.getMaxSectionY(); y++)
                    dirtySections.add(net.minecraft.core.SectionPos.asLong(x + dx, y, z + dz));
        }
        private static void publishDirtySections(ClientLevel world) {
            for (long section : dirtySections) {
                var pos = net.minecraft.core.SectionPos.of(section);
                var chunk = world.getChunkSource().getChunkNow(pos.x(), pos.z());
                // Empty/unloaded sections own no terrain mesh. Their neighbors
                // remain independently dirty, including fitted overhanging snow.
                if (chunk == null || pos.y() < world.getMinSectionY() || pos.y() > world.getMaxSectionY()
                        || chunk.getSection(world.getSectionIndexFromSectionY(pos.y())).hasOnlyAir()) continue;
                // Optical skylight only raises vanilla light. If the entire
                // sampling halo is already 15, no resulting vertex can change.
                if (fullyLitHalo(world, pos)) continue;
                world.getChunkSource().onLightUpdate(net.minecraft.world.level.LightLayer.SKY, pos);
            }
            dirtySections.clear();
        }

        private static boolean fullyLitHalo(ClientLevel world, net.minecraft.core.SectionPos pos) {
            var vanilla = world.getLightEngine().getLayerListener(net.minecraft.world.level.LightLayer.SKY);
            var raw = rawLight.get();
            raw[0] = true;
            try {
                for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) {
                    var data = vanilla.getDataLayerData(net.minecraft.core.SectionPos.of(pos.x() + x, pos.y() + y, pos.z() + z));
                    if (data == null || !data.isDefinitelyFilledWith(15)) return false;
                }
                return true;
            } finally { raw[0] = false; }
        }

        private static final class OpticalChunk implements net.minecraft.world.level.chunk.LightChunk {
            final net.minecraft.world.level.chunk.LevelChunk chunk;
            final net.minecraft.world.level.lighting.ChunkSkyLightSources sources;
            OpticalChunk(net.minecraft.world.level.chunk.LevelChunk chunk) {
                this.chunk = chunk;
                sources = new net.minecraft.world.level.lighting.ChunkSkyLightSources(chunk);
                // Seed from the real table, then remove only water's obstruction.
                sources.fillFrom(chunk);
                for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    int y = sources.getLowestSourceY(x, z);
                    if (y > chunk.getMinY()) sources.update(this, x, y - 1, z);
                }
            }
            @Override public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos pos) {
                var state = chunk.getBlockState(pos);
                // Keep waterlogged solid shapes, ice, leaves and roofs intact.
                if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED))
                    return state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false);
                return state.getFluidState().is(FluidTags.WATER) && state.getCollisionShape(chunk, pos).isEmpty()
                    ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : state;
            }
            @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
            @Override public net.minecraft.world.level.block.entity.@org.jspecify.annotations.Nullable BlockEntity getBlockEntity(BlockPos pos) { return chunk.getBlockEntity(pos); }
            @Override public int getHeight() { return chunk.getHeight(); }
            @Override public int getMinY() { return chunk.getMinY(); }
            @Override public void findBlockLightSources(java.util.function.BiConsumer<BlockPos, net.minecraft.world.level.block.state.BlockState> consumer) {}
            @Override public net.minecraft.world.level.lighting.ChunkSkyLightSources getSkyLightSources() { return sources; }
        }
        public static void changed(ClientLevel source, BlockPos pos) {
            if (level == source) changed.add(pos.immutable());
            environmentRevision++;
        }
        public static int light(Object source, BlockPos pos, int vanilla) {
            var current = engine;
            return source == original && current != null && chunks.containsKey(net.minecraft.world.level.ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4))
                ? Math.max(vanilla, current.getLayerListener(net.minecraft.world.level.LightLayer.SKY).getLightValue(pos)) : vanilla;
        }
        public static net.minecraft.world.level.chunk.@org.jspecify.annotations.Nullable DataLayer data(Object source, net.minecraft.core.SectionPos pos,
                net.minecraft.world.level.chunk.@org.jspecify.annotations.Nullable DataLayer vanilla) {
            var current = engine;
            if (rawLight.get()[0] || source != original || current == null || !chunks.containsKey(net.minecraft.world.level.ChunkPos.pack(pos.x(), pos.z()))) return vanilla;
            var extra = current.getLayerListener(net.minecraft.world.level.LightLayer.SKY).getDataLayerData(pos);
            if (extra == null) return vanilla;
            if (vanilla == null) return extra;
            return naturality.client.api.rendering.LightLayers.mergeLightData(vanilla, extra);
        }
        private static void tick(Minecraft client) {
            var next = client.level;
            if (next != level || !NaturalityConfig.get().liquids.waterDepth) {
                dirtySections.clear();
                if (level == next && next != null) for (var chunk : chunks.values())
                    dirtyColumn(next, chunk.chunk.getPos().x(), chunk.chunk.getPos().z());
                original = null;
                engine = null;
                chunks.clear();
                changed.clear();
                level = next;
                if (next != null) publishDirtySections(next);

            }
            if (next == null || !next.dimensionType().hasSkyLight() || !NaturalityConfig.get().liquids.waterDepth || client.isPaused()) return;
            if (engine == null) {
                original = next.getLightEngine().getLayerListener(net.minecraft.world.level.LightLayer.SKY);
                engine = new net.minecraft.world.level.lighting.LevelLightEngine(new net.minecraft.world.level.chunk.LightChunkGetter() {
                    @Override public net.minecraft.world.level.chunk.@org.jspecify.annotations.Nullable LightChunk getChunkForLighting(int x, int z) {
                        return chunks.get(net.minecraft.world.level.ChunkPos.pack(x, z));
                    }
                    @Override public net.minecraft.world.level.BlockGetter getLevel() { return next; }
                    @Override public void onLightUpdate(net.minecraft.world.level.LightLayer layer, net.minecraft.core.SectionPos pos) {
                        dirtySections.add(pos.asLong());
                    }
                }, false, true);
            }
            var current = engine;
            if (current == null) return;
            var camera = client.gameRenderer.mainCamera().blockPosition();
            int cx = camera.getX() >> 4, cz = camera.getZ() >> 4;
            int range = client.options.getEffectiveRenderDistance() + 1;
            var removed = new java.util.ArrayList<Long>();
            for (var entry : chunks.entrySet()) {
                var pos = entry.getValue().chunk.getPos();
                var loaded = next.getChunkSource().getChunk(pos.x(), pos.z(), net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
                if (Math.abs(pos.x() - cx) > range || Math.abs(pos.z() - cz) > range || loaded != entry.getValue().chunk) removed.add(entry.getKey());
            }
            for (long key : removed) {
                var chunk = chunks.remove(key).chunk;
                dirtyColumn(next, chunk.getPos().x(), chunk.getPos().z());
                current.setLightEnabled(chunk.getPos(), false);
                for (int y = next.getMinSectionY(); y <= next.getMaxSectionY(); y++)
                    current.updateSectionStatus(net.minecraft.core.SectionPos.of(chunk.getPos(), y), true);
            }
            // Load nearest columns first, with a bounded amount of work each tick.
            int added = 0;
            // Keep the original row-major ring order and four-column admission
            // budget, but do not iterate over the rejected square interiors.
            outer: for (int radius = 0; radius <= range; radius++) for (int dz = -radius; dz <= radius; dz++)
                    for (int dx = -radius, step = Math.abs(dz) == radius ? 1 : radius * 2; dx <= radius; dx += step) {
                long key = net.minecraft.world.level.ChunkPos.pack(cx + dx, cz + dz);
                if (chunks.containsKey(key)) continue;
                var chunk = next.getChunkSource().getChunk(cx + dx, cz + dz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
                if (chunk == null) continue;
                chunks.put(key, new OpticalChunk(chunk));
                dirtyColumn(next, chunk.getPos().x(), chunk.getPos().z());
                for (int y = next.getMinSectionY(); y <= next.getMaxSectionY(); y++)
                    current.updateSectionStatus(net.minecraft.core.SectionPos.of(chunk.getPos(), y), chunk.getSection(next.getSectionIndexFromSectionY(y)).hasOnlyAir());
                current.runLightUpdates();
                current.propagateLightSources(chunk.getPos());
                // Sources previously bordering an unavailable column must emit
                // again now that its light storage exists.
                for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    var neighbor = chunks.get(net.minecraft.world.level.ChunkPos.pack(cx + dx + direction.getStepX(), cz + dz + direction.getStepZ()));
                    if (neighbor != null) current.propagateLightSources(neighbor.chunk.getPos());
                }
                if (++added >= 4) break outer;
            }
            for (var pos : changed) {
                var chunk = chunks.get(net.minecraft.world.level.ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
                if (chunk == null) continue;
                chunk.sources.update(chunk, pos.getX() & 15, pos.getY(), pos.getZ() & 15);
                current.updateSectionStatus(net.minecraft.core.SectionPos.of(pos), chunk.chunk.getSection(next.getSectionIndex(pos.getY())).hasOnlyAir());
                current.checkBlock(pos);
            }
            changed.clear();
            current.runLightUpdates();
            // Only schedule meshes after every source and neighboring column in
            // this batch has published its final light storage.
            publishDirtySections(next);
        }
    }
    private static final int[] rawPixels = new int[SIZE * SIZE];
    private static boolean mapChanged;
    private static final ColumnTile[] mappedTiles = new ColumnTile[256];
    // Retain overlap when the 256-block window moves; bound memory across travel.
    private static final it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<ColumnTile> columnCache =
        new it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<>(384);
    private static final ByteBuffer frameData = ByteBuffer.allocateDirect(448).order(ByteOrder.nativeOrder());
    private static final class ColumnTile {
        final int[] pixels = new int[256], murk = new int[256];
        final java.lang.ref.WeakReference<net.minecraft.world.level.chunk.LevelChunk> chunk;
        boolean dirty = true;
        int wet;
        ColumnTile(net.minecraft.world.level.chunk.@org.jspecify.annotations.Nullable LevelChunk chunk) { this.chunk = new java.lang.ref.WeakReference<>(chunk); }
    }
    public static void invalidateChunk(ClientLevel changedWorld, int x, int z) {
        if (world != changedWorld) return;
        environmentRevision++;
        var cached = columnCache.get(net.minecraft.world.level.ChunkPos.pack(x, z));
        if (cached != null) cached.dirty = true;
    }
    public static void invalidateChunkAndNeighbors(ClientLevel changedWorld, int x, int z) {
        // BiomeManager's quart interpolation can read just across a chunk boundary.
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            invalidateChunk(changedWorld, x + dx, z + dz);
    }
    public static void invalidateBiomes() {
        environmentRevision++;
        for (var tile : columnCache.values()) tile.dirty = true;
    }
    private static final int[] turbidity = new int[SIZE * SIZE];
    private static final float[] blurred = new float[SIZE * SIZE];
    private static final boolean[] murkRows = new boolean[SIZE], filteredColumns = new boolean[SIZE];
    private static final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private WaterVisuals() {}
    private static boolean fallingWater(FluidState fluid) {
        return fluid.is(FluidTags.WATER) && !fluid.isSource() && fluid.getValue(FlowingFluid.FALLING);
    }
    public static void initialize() { naturality.client.systems.sleep.SleepVisuals.register(WaterVisuals::tick); }

    public static boolean enabled() {
        var client = Minecraft.getInstance();
        var level = client.level;
        var c = NaturalityConfig.get().liquids;
        if (level == null || !(c.waterDepth || c.waterDistortion || c.waterShimmer)) return false;
        var camera = client.gameRenderer.mainCamera();
        if (camera.getFluidInCamera() == FogType.LAVA || camera.getFluidInCamera() == FogType.POWDER_SNOW) return false;
        return !(camera.entity() instanceof LivingEntity living
            && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS)));
    }

    public static boolean ready() {
        var client = Minecraft.getInstance();
        var level = client.level;
        return initialized && world == level && (wetColumns > 0 || besideSubmergedWater
            || client.gameRenderer.mainCamera().getFluidInCamera() == FogType.WATER) && enabled();
    }

    private static void allocate() {
        if (image != null) return;
        image = new NativeImage(SIZE, SIZE, true);
        var texture = RenderSystem.getDevice().createTexture(() -> "Naturality water columns", 5,
            GpuFormat.RGBA8_UNORM, SIZE, SIZE, 1, 1);
        WaterVisuals.texture = texture;
        view = RenderSystem.getDevice().createTextureView(texture);
    }

    private static void tick(Minecraft client) {
        Skylight.tick(client);
        var level = client.level;
        if (level == null) {
            besideSubmergedWater = false;
            world = null;
            depthWorld = null;
            depthPosition = null;
            depthRevision = -1;
            initialized = false;
            columnCache.clear();
            java.util.Arrays.fill(mappedTiles, null);
            return;
        }
        if (!enabled() || client.isPaused()) return;
        allocate();
        mapChanged = !initialized || world != level;
        if (mapChanged) {
            java.util.Arrays.fill(murkRows, true);
            java.util.Arrays.fill(filteredColumns, true);
        }
        if (world != level) { columnCache.clear(); java.util.Arrays.fill(mappedTiles, null); }
        var p = client.gameRenderer.mainCamera().blockPosition();
        // A small dry pocket can sit below a roof, outside the exposed-column
        // map. Remember nearby full water once per tick; this enables only the
        // masked view through its boundary, never immersion/breathing in air.
        besideSubmergedWater = false;
        for (int dz = -1; dz <= 1 && !besideSubmergedWater; dz++) for (int dx = -1; dx <= 1; dx++) {
            var chunk = level.getChunkSource().getChunk((p.getX() + dx) >> 4, (p.getZ() + dz) >> 4, false);
            if (chunk == null) continue;
            cursor.set(p.getX() + dx, p.getY(), p.getZ() + dz);
            var fluid = chunk.getFluidState(cursor);
            if (!fluid.is(FluidTags.WATER) || fallingWater(fluid)) continue;
            cursor.move(0, 1, 0);
            var above = chunk.getBlockState(cursor);
            if (above.getFluidState().is(FluidTags.WATER) || above.isSolidRender()) {
                besideSubmergedWater = true;
                break;
            }
        }
        int x = Math.floorDiv(p.getX(), 32) * 32 - SIZE / 2;
        int z = Math.floorDiv(p.getZ(), 32) * 32 - SIZE / 2;
        if (!initialized || world != level || x != originX || z != originZ) {
            world = level;
            originX = x;
            originZ = z;
            for (int tile = 0; tile < 256; tile++) updateTile(tile);
            initialized = true;
        } else {
            // Eight chunks per tick; geometry/biome edits converge in at most 1.6 seconds.
            for (int i = 0; i < 8; i++) updateTile(nextTile++ & 255);
            updateTile(((p.getZ() - originZ) >> 4) * 16 + ((p.getX() - originX) >> 4));
        }
        if (!mapChanged) return;
        environmentRevision++;
        smoothTurbidity();
        var texture = WaterVisuals.texture;
        var image = WaterVisuals.image;
        if (texture == null || image == null) return;
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image, 0, 0, 0, 0);
    }

    private static void updateTile(int tile) {
        var world = WaterVisuals.world;
        var image = WaterVisuals.image;
        if (world == null || image == null) return;
        int sx = (tile & 15) * 16, sz = (tile >> 4) * 16;
        var chunk = world.getChunkSource().getChunk((originX + sx) >> 4, (originZ + sz) >> 4, false);
        long key = net.minecraft.world.level.ChunkPos.pack((originX + sx) >> 4, (originZ + sz) >> 4);
        var cached = columnCache.getAndMoveToLast(key);
        if (cached == null || cached.chunk.get() != chunk) {
            cached = new ColumnTile(chunk);
            columnCache.put(key, cached);
            if (columnCache.size() > 384) columnCache.removeFirst();
        }
        if (!cached.dirty && mappedTiles[tile] == cached) return;
        if (cached.dirty) {
            int wet = 0;
            for (int z = sz; z < sz + 16; z++) for (int x = sx; x < sx + 16; x++) {
                int packed = 0;
                int local = (z - sz) * 16 + x - sx;
                cached.murk[local] = 0;
                if (chunk != null) {
                    int wx = originX + x, wz = originZ + z;
                    int highestBlock = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
                    int top = highestBlock;
                    cursor.set(wx, top, wz);
                    // Lily pads / vegetation above the water are permitted; solid roofs are not.
                    for (int i = 0; i < 16 && top > world.getMinY(); i++, top--) {
                        cursor.setY(top);
                        var state = chunk.getBlockState(cursor);
                        if (state.getFluidState().is(FluidTags.WATER)) break;
                        if (state.isSolidRender()) break;
                    }
                    cursor.setY(top);
                    var biome = world.getBiome(cursor);
                    cached.murk[local] = biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) ? 254 : 0;
                    if (chunk.getFluidState(cursor).is(FluidTags.WATER)) {
                        int bottom = top;
                        int poolTop = fallingWater(chunk.getFluidState(cursor)) ? Integer.MIN_VALUE : top;
                        while (bottom > world.getMinY() && top - bottom < 254) {
                            cursor.setY(bottom - 1);
                            var fluid = chunk.getFluidState(cursor);
                            if (!fluid.is(FluidTags.WATER)) break;
                            bottom--;
                            // A falling sheet is not a deep pool. Keep the receiving
                            // pool below it, with its own surface and actual depth.
                            if (fallingWater(fluid)) poolTop = Integer.MIN_VALUE;
                            else if (poolTop == Integer.MIN_VALUE) poolTop = bottom;
                        }
                        cursor.setY(top);
                        if (poolTop != Integer.MIN_VALUE) {
                            int murk = cached.murk[local] + 1;
                            // Reserve the high height bit for a geometric sky-cover flag.
                            // WORLD_SURFACE includes ice, snow and other non-opaque covers.
                            int height = Math.clamp(poolTop - world.getMinY(), 0, 32767);
                            if (highestBlock > poolTop) height |= 0x8000;
                            packed = murk << 24 | (height & 255) << 16 | (height >> 8) << 8 | (poolTop - bottom + 1);
                        }
                    }
                }
                cached.pixels[local] = packed;
                if (packed != 0) wet++;
            }
            cached.wet = wet;
            cached.dirty = false;
        }
        int minMurkX = SIZE, maxMurkX = -1;
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int local = z * 16 + x, index = (sz + z) * SIZE + sx + x;
            if (rawPixels[index] != cached.pixels[local]) {
                filteredColumns[sx + x] = true;
                mapChanged = true;
            }
            if (turbidity[index] != cached.murk[local]) {
                murkRows[sz + z] = true;
                minMurkX = Math.min(minMurkX, sx + x);
                maxMurkX = Math.max(maxMurkX, sx + x);
                mapChanged = true;
            }
            rawPixels[index] = cached.pixels[local];
            turbidity[index] = cached.murk[local];
        }
        if (maxMurkX >= minMurkX)
            java.util.Arrays.fill(filteredColumns, Math.max(0, minMurkX - 12), Math.min(SIZE, maxMurkX + 13), true);
        wetColumns += cached.wet - wetTiles[tile];
        wetTiles[tile] = cached.wet;
        mappedTiles[tile] = cached;
    }

    // A separable 25-block box filter softens biome changes without blurring
    // surface/bottom heights or mixing dry cells into the water geometry mask.
    private static void smoothTurbidity() {
        var image = WaterVisuals.image;
        if (image == null) return;
        final int radius = 12, span = radius * 2 + 1;
        for (int z = 0; z < SIZE; z++) {
            if (!murkRows[z]) continue;
            murkRows[z] = false;
            int sum = 0;
            for (int i = -radius; i <= radius; i++) sum += turbidity[z * SIZE + Math.clamp(i, 0, SIZE - 1)];
            for (int x = 0; x < SIZE; x++) {
                blurred[z * SIZE + x] = (float) sum / span;
                sum += turbidity[z * SIZE + Math.clamp(x + radius + 1, 0, SIZE - 1)]
                    - turbidity[z * SIZE + Math.clamp(x - radius, 0, SIZE - 1)];
            }
        }
        for (int x = 0; x < SIZE; x++) {
            if (!filteredColumns[x]) continue;
            filteredColumns[x] = false;
            // Retain the full vertical accumulation order: restarting a float
            // sum at a dirty rectangle can change rounding at biome boundaries.
            float sum = 0;
            for (int i = -radius; i <= radius; i++) sum += blurred[Math.clamp(i, 0, SIZE - 1) * SIZE + x];
            for (int z = 0; z < SIZE; z++) {
                int pixel = rawPixels[z * SIZE + x];
                image.setPixel(x, z, (pixel >>> 24) == 0 ? 0 :
                    (pixel & 0xFFFFFF) | (1 + Math.clamp(Math.round(sum / span), 0, 254)) << 24);
                sum += blurred[Math.clamp(z + radius + 1, 0, SIZE - 1) * SIZE + x]
                    - blurred[Math.clamp(z - radius, 0, SIZE - 1) * SIZE + x];
            }
        }
    }

    /** Test/diagnostic query: surface Y, or NaN outside a known wet column. */
    public static float surface(int x, int z) {
        var image = WaterVisuals.image;
        var world = WaterVisuals.world;
        if (image == null || world == null) return Float.NaN;
        if (!initialized || world != Minecraft.getInstance().level || x < originX || z < originZ
                || x >= originX + SIZE || z >= originZ + SIZE) return Float.NaN;
        int p = image.getPixel(x - originX, z - originZ);
        return (p >>> 24) == 0 ? Float.NaN : world.getMinY() + ((p >> 16) & 255) + ((p >> 8) & 127) * 256 + 0.875F;
    }

    public static float turbidityAt(int x, int z) {
        var image = WaterVisuals.image;
        if (image == null) return 0;
        if (Float.isNaN(surface(x, z))) return Float.NaN;
        return ((image.getPixel(x - originX, z - originZ) >>> 24) - 1) / 254.0F;
    }

    public static void prepareFrame() {
        allocate();
        var client = Minecraft.getInstance();
        var level = client.level;
        var camera = client.gameRenderer.mainCamera();
        var p = camera.position();
        var c = NaturalityConfig.get().liquids;
        boolean active = ready();
        float sun = 0;
        if (level != null && level.dimensionType().hasSkyLight()) {
            float angle = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, 1.0F);
            sun = Math.clamp((float) Math.cos(Math.toRadians(angle)) * 2.0F, 0, 1)
                * (1 - level.getRainLevel(1.0F)) * (1 - level.getThunderLevel(1.0F));
        }
        boolean nightVision = camera.entity() instanceof LivingEntity living && living.hasEffect(MobEffects.NIGHT_VISION);
        boolean immersed = camera.getFluidInCamera() == FogType.WATER;
        boolean inWaterfall = level != null && fallingWater(level.getFluidState(camera.blockPosition()));
        float cameraDepth = 0, localLight = 0;
        int tint = 0x3F76E4;
        if (level != null) {
            tint = net.minecraft.client.renderer.BiomeColors.getAverageWaterColor(level, camera.blockPosition());
            localLight = Math.max(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, camera.blockPosition()),
                level.getBrightness(net.minecraft.world.level.LightLayer.SKY, camera.blockPosition()) * sun) / 15.0F;
            boolean roofedAirPocket = !immersed && besideSubmergedWater
                && !level.canSeeSky(camera.blockPosition());
            if ((immersed || roofedAirPocket) && !inWaterfall) {
                // Reuse identical camera-column queries between frames. Vertical
                // motion also invalidates roof recovery and preserves the exact
                // depth arithmetic, including near-surface clamping.
                var cachedPosition = depthPosition;
                if (depthWorld != level || depthRevision != environmentRevision || cachedPosition == null
                        || !camera.blockPosition().equals(cachedPosition) || depthEyeY != p.y) {
                    var scan = camera.blockPosition().mutable();
                    int n = 0;
                    while (n++ < 255 && scan.getY() < level.getMaxY()
                            && level.getFluidState(scan).is(FluidTags.WATER)
                            && !fallingWater(level.getFluidState(scan))) scan.move(0, 1, 0);
                    cameraDepth = Math.max(0, (float) (scan.getY() - 0.125 - p.y));
                    boolean cachedRoof = !level.canSeeSky(scan) && !fallingWater(level.getFluidState(scan));
                    // Air below a roof is not the open water surface either.
                    // Recover ocean depth across geodes/air pockets, including
                    // the dry side of their boundary, without forcing immersion.
                    if (cachedRoof) {
                        float sum = 0, weight = 0;
                        for (int dz = -16; dz <= 16; dz += 2) for (int dx = -16; dx <= 16; dx += 2) {
                            float top = surface(camera.blockPosition().getX() + dx, camera.blockPosition().getZ() + dz);
                            if (!Float.isFinite(top) || top < p.y) continue;
                            float w = 1.0F / (1 + dx * dx + dz * dz);
                            sum += top * w;
                            weight += w;
                        }
                        if (weight > 0) cameraDepth = Math.max(cameraDepth, sum / weight - (float) p.y);
                    }
                    cachedDepth = cameraDepth;
                    depthWorld = level;
                    depthPosition = camera.blockPosition().immutable();
                    depthRevision = environmentRevision;
                    depthEyeY = p.y;
                }
                cameraDepth = cachedDepth;
            }
        }
        frameDepth = cameraDepth;
        float murk = cameraMurk(client);
        float renderDistanceBlocks = client.options.getEffectiveRenderDistance() * 16.0F;
        float visibility = visibilityRange(renderDistanceBlocks, murk);
        frameVisibilityRange = active && immersed && c.waterDepth && !Waterline.active() ? visibility : Float.POSITIVE_INFINITY;
        // Nearer visible pixels can refract from farther source pixels. The
        // optics depth rejection reaches at most max(2.25, 0.09 * distance).
        float shadeLimit = Float.isFinite(frameVisibilityRange)
            ? visibility + Math.max(3.0F, visibility * 0.10F) + 0.25F : 0.0F;

        ByteBuffer data = frameData.clear();
        data.putInt(originX).putInt(originZ).putInt(world == null ? 0 : world.getMinY()).putInt(active ? 1 : 0);
        data.putFloat((float) (p.x - originX)).putFloat((float) p.y).putFloat((float) (p.z - originZ)).putFloat(sun);
        data.putFloat(visibility).putFloat(shadeLimit).putFloat(c.waterPixelSize).putFloat(renderDistanceBlocks);
        data.putInt(c.waterDepth ? 1 : 0).putInt(c.waterDistortion ? 1 : 0).putInt(c.waterShimmer ? 1 : 0).putInt(nightVision ? 1 : 0);
        data.putFloat((ProceduralFluids.ticks() + client.getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 20.0F)
            .putFloat(immersed ? 1 : 0).putFloat(cameraDepth).putFloat(localLight);
        data.putFloat(((tint >> 16) & 255) / 255.0F).putFloat(((tint >> 8) & 255) / 255.0F)
            .putFloat((tint & 255) / 255.0F).putFloat(murk);
        boolean atmosphere = level != null && level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
            && (camera.getFluidInCamera() == FogType.NONE || Waterline.active())
            && !(camera.entity() instanceof LivingEntity e && (e.hasEffect(MobEffects.BLINDNESS) || e.hasEffect(MobEffects.DARKNESS)))
            && !client.gui.hud.getBossOverlay().shouldCreateWorldFog();
        var weather = level == null ? null : naturality.systems.weather.framework.WeatherSystem.renderState(level);
        float rainFog = atmosphere && weather != null ? weather.heavyRainFog() : 0;
        data.putInt(atmosphere ? 1 : 0).putInt(inWaterfall ? 1 : 0)
            .putInt(Float.floatToRawIntBits(rainFog)).putInt(besideSubmergedWater ? 1 : 0);
        Waterline.write(data, camera);
        writeFogColor(data, active, (float) (p.x - originX), (float) p.y, (float) (p.z - originZ),
            sun, localLight, nightVision, tint);
        data.flip();
        var ring = uniform;
        if (ring == null) { ring = new MappableRingBuffer(() -> "Naturality water frame", 130, data.capacity()); uniform = ring; }
        else ring.rotate();
        try (var mapped = ring.currentBuffer().map(false, true)) { mapped.data().put(data); }
    }

    private static void writeFogColor(ByteBuffer data, boolean active, float x, float y, float z,
            float sun, float localLight, boolean nightVision, int tint) {
        // Decode the uploaded map, not a fresh world query. Use the same float
        // camera coordinates, floor, inclusive heights and packed channels as
        // naturality_water_geometry, including at negative/world-border positions.
        var image = WaterVisuals.image;
        var world = WaterVisuals.world;
        int cellX = (int) Math.floor(x), cellZ = (int) Math.floor(z);
        boolean exposed = false;
        if (active && image != null && world != null && cellX >= 0 && cellX < SIZE && cellZ >= 0 && cellZ < SIZE) {
            int pixel = image.getPixel(cellX, cellZ);
            float top = (float) (world.getMinY() + ((pixel >> 16) & 255) + ((pixel >> 8) & 127) * 256) + 0.875F;
            float bottom = top - (pixel & 255) + 0.125F;
            exposed = (pixel >>> 24) != 0 && y >= bottom && y <= top;
        }
        float daylight = exposed ? sun : 0.0F;
        float ambient = Math.min(localLight, sun);
        float light = nightVision ? 1.0F : Math.clamp(Math.max(ambient, daylight), 0.0F, 1.0F);
        float brightness = 0.70F * (float) Math.sqrt(light);
        data.putFloat(((tint >> 16) & 255) / 255.0F * brightness)
            .putFloat(((tint >> 8) & 255) / 255.0F * brightness)
            .putFloat((tint & 255) / 255.0F * brightness).putFloat(0.0F);
    }

    public static void bind(RenderPass pass) {
        if (uniform == null) prepareFrame();
        var uniform = java.util.Objects.requireNonNull(WaterVisuals.uniform, "Water frame was prepared");
        pass.setUniform("NaturalityWater", uniform.currentBuffer());
        pass.setUniform("NaturalityWaterColumns", view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
    }

    private static float cameraMurk(Minecraft client) {
        var level = client.level;
        if (level == null) return 0;
        var pos = client.gameRenderer.mainCamera().blockPosition();
        float mapped = turbidityAt(pos.getX(), pos.getZ());
        if (Float.isFinite(mapped)) return mapped;
        var biome = level.getBiome(pos);
        return biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) ? 1 : 0;
    }

    public static void bindScene(RenderPass pass) {
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var atmosphere = naturality.client.rendering.fog.EndFogComposite.atmosphereView();
        pass.setUniform("NaturalityWaterAtmosphere", atmosphere == null ? view : atmosphere, sampler);
        pass.setUniform("NaturalityWaterMask", WaterComposite.maskView() == null ? view : WaterComposite.maskView(), sampler);
        pass.setUniform("NaturalityWaterOpaqueDepth", WaterComposite.depthView() == null ? view : WaterComposite.depthView(), sampler);
    }

    public static void bindSurfaceMask(RenderPass pass) {
        allocate();
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var surface = WaterComposite.maskView();
        var maskedDepth = WaterComposite.maskDepthView();
        var opaqueDepth = WaterComposite.depthView();
        // Identical fallback depth samplers cannot report a hull. Never reuse
        // a previous frame's capture or sample a depth attachment being written.
        boolean captured = surface != null && maskedDepth != null && opaqueDepth != null;
        pass.setUniform("NaturalityWaterMask", captured ? surface : view, sampler);
        pass.setUniform("NaturalityWaterMaskDepth", captured ? maskedDepth : view, sampler);
        pass.setUniform("NaturalityWaterOpaqueDepth", captured ? opaqueDepth : view, sampler);
    }

    public static void close() {
        frameVisibilityRange = Float.POSITIVE_INFINITY;
        SunTransmission.close();
        if (uniform != null) { uniform.close(); uniform = null; }
        if (view != null) { view.close(); view = null; }
        if (texture != null) { texture.close(); texture = null; }
        if (image != null) { image.close(); image = null; }
        initialized = false;
        columnCache.clear();
        java.util.Arrays.fill(mappedTiles, null);
        java.util.Arrays.fill(rawPixels, 0);
        java.util.Arrays.fill(turbidity, 0);
        world = null;
        wetColumns = 0;
        besideSubmergedWater = false;
        java.util.Arrays.fill(wetTiles, 0);
    }
}
