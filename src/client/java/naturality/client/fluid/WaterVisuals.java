package naturality.client.fluid;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import naturality.config.NaturalityConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
    private static @org.jspecify.annotations.Nullable GpuBuffer uniform;
    private static int originX, originZ, nextTile;
    private static boolean initialized;
    private static final int[] wetTiles = new int[256];
    private static int wetColumns;
    private static float frameDepth;
    public static float cameraDepth() { return frameDepth; }
    private static final int[] turbidity = new int[SIZE * SIZE];
    private static final float[] blurred = new float[SIZE * SIZE];
    private static final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private WaterVisuals() {}
    private static boolean fallingWater(FluidState fluid) {
        return fluid.is(FluidTags.WATER) && !fluid.isSource() && fluid.getValue(FlowingFluid.FALLING);
    }
    public static void initialize() { ClientTickEvents.END_CLIENT_TICK.register(WaterVisuals::tick); }

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
        return initialized && world == level && (wetColumns > 0
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
        var level = client.level;
        if (level == null) { world = null; initialized = false; return; }
        if (!enabled() || client.isPaused()) return;
        allocate();
        var p = client.gameRenderer.mainCamera().blockPosition();
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
        int wet = 0;
        int sx = (tile & 15) * 16, sz = (tile >> 4) * 16;
        var chunk = world.getChunkSource().getChunk((originX + sx) >> 4, (originZ + sz) >> 4, false);
        for (int z = sz; z < sz + 16; z++) for (int x = sx; x < sx + 16; x++) {
            int packed = 0;
            turbidity[z * SIZE + x] = 0;
            if (chunk != null) {
                int wx = originX + x, wz = originZ + z;
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
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
                turbidity[z * SIZE + x] = biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) ? 254 : 0;
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
                        int murk = turbidity[z * SIZE + x] + 1;
                        int height = Math.clamp(poolTop - world.getMinY(), 0, 65535);
                        packed = murk << 24 | (height & 255) << 16 | (height >> 8) << 8 | (poolTop - bottom + 1);
                    }
                }
            }
            image.setPixel(x, z, packed);
            if (packed != 0) wet++;
        }
        wetColumns += wet - wetTiles[tile];
        wetTiles[tile] = wet;
    }

    // A separable 25-block box filter softens biome changes without blurring
    // surface/bottom heights or mixing dry cells into the water geometry mask.
    private static void smoothTurbidity() {
        var image = WaterVisuals.image;
        if (image == null) return;
        final int radius = 12, span = radius * 2 + 1;
        for (int z = 0; z < SIZE; z++) {
            int sum = 0;
            for (int i = -radius; i <= radius; i++) sum += turbidity[z * SIZE + Math.clamp(i, 0, SIZE - 1)];
            for (int x = 0; x < SIZE; x++) {
                blurred[z * SIZE + x] = (float) sum / span;
                sum += turbidity[z * SIZE + Math.clamp(x + radius + 1, 0, SIZE - 1)]
                    - turbidity[z * SIZE + Math.clamp(x - radius, 0, SIZE - 1)];
            }
        }
        for (int x = 0; x < SIZE; x++) {
            float sum = 0;
            for (int i = -radius; i <= radius; i++) sum += blurred[Math.clamp(i, 0, SIZE - 1) * SIZE + x];
            for (int z = 0; z < SIZE; z++) {
                int pixel = image.getPixel(x, z);
                if ((pixel >>> 24) != 0) image.setPixel(x, z,
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
        return (p >>> 24) == 0 ? Float.NaN : world.getMinY() + ((p >> 16) & 255) + ((p >> 8) & 255) * 256 + 0.875F;
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
            if (immersed && !inWaterfall) {
                var scan = camera.blockPosition().mutable();
                int n = 0;
                while (n++ < 255 && scan.getY() < level.getMaxY()
                        && level.getFluidState(scan).is(FluidTags.WATER)
                        && !fallingWater(level.getFluidState(scan))) scan.move(0, 1, 0);
                cameraDepth = Math.max(0, (float) (scan.getY() - 0.125 - p.y));
                // A solid overhang is not the water surface. Recover the local
                // exposed water level instead of treating its underside as air.
                if (!level.getBlockState(scan).isAir() && !fallingWater(level.getFluidState(scan))) {
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
            }
        }
        frameDepth = cameraDepth;
        ByteBuffer data = ByteBuffer.allocateDirect(112).order(ByteOrder.nativeOrder());
        data.putInt(originX).putInt(originZ).putInt(world == null ? 0 : world.getMinY()).putInt(active ? 1 : 0);
        data.putFloat((float) (p.x - originX)).putFloat((float) p.y).putFloat((float) (p.z - originZ)).putFloat(sun);
        data.putFloat(c.waterDarkDepth).putFloat(8).putFloat(c.waterPixelSize).putFloat(client.options.getEffectiveRenderDistance() * 16.0F);
        data.putInt(c.waterDepth ? 1 : 0).putInt(c.waterDistortion ? 1 : 0).putInt(c.waterShimmer ? 1 : 0).putInt(nightVision ? 1 : 0);
        data.putFloat(ProceduralFluids.ticks() / 20.0F).putFloat(immersed ? 1 : 0).putFloat(cameraDepth).putFloat(localLight);
        data.putFloat(((tint >> 16) & 255) / 255.0F).putFloat(((tint >> 8) & 255) / 255.0F)
            .putFloat((tint & 255) / 255.0F).putFloat(cameraMurk(client));
        boolean atmosphere = level != null && level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
            && camera.getFluidInCamera() == FogType.NONE
            && !(camera.entity() instanceof LivingEntity e && (e.hasEffect(MobEffects.BLINDNESS) || e.hasEffect(MobEffects.DARKNESS)))
            && !client.gui.hud.getBossOverlay().shouldCreateWorldFog();
        data.putInt(atmosphere ? 1 : 0).putInt(inWaterfall ? 1 : 0).putInt(0).putInt(0);
        data.flip();
        if (uniform != null) uniform.close();
        uniform = RenderSystem.getDevice().createBuffer(() -> "Naturality water frame", 128, data);
    }

    public static void bind(RenderPass pass) {
        if (uniform == null) prepareFrame();
        var uniform = java.util.Objects.requireNonNull(WaterVisuals.uniform, "Water frame was prepared");
        pass.setUniform("NaturalityWater", uniform);
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
        var atmosphere = naturality.client.fog.EndFogComposite.atmosphereView();
        pass.setUniform("NaturalityWaterAtmosphere", atmosphere == null ? view : atmosphere, sampler);
        pass.setUniform("NaturalityWaterMask", WaterComposite.maskView() == null ? view : WaterComposite.maskView(), sampler);
        pass.setUniform("NaturalityWaterOpaqueDepth", WaterComposite.depthView() == null ? view : WaterComposite.depthView(), sampler);
    }

    public static void close() {
        if (uniform != null) { uniform.close(); uniform = null; }
        if (view != null) { view.close(); view = null; }
        if (texture != null) { texture.close(); texture = null; }
        if (image != null) { image.close(); image = null; }
        initialized = false;
        world = null;
        wetColumns = 0;
        java.util.Arrays.fill(wetTiles, 0);
    }
}
