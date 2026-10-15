package naturality.client.blocks.fire;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.RenderPipelines;
import naturality.Naturality;
import com.mojang.renderpearl.api.pipeline.*;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import java.util.SplittableRandom;

@org.jspecify.annotations.NullMarked
public final class ProceduralFire {
    public static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
        .withUniform("NaturalityFire", UniformType.UNIFORM_BUFFER)
        .withUniform("NaturalityFireHeat", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("NaturalityFirePalette", UniformType.COMBINED_IMAGE_SAMPLER).build();
    // Use a uniform clock to stay within Sodium OIT's texture-unit budget.
    public static final BindGroupLayout SODIUM_LAYOUT = BindGroupLayout.builder()
        .withUniform("NaturalityFire", UniformType.UNIFORM_BUFFER)
        .withUniform("NaturalityFirePalette", UniformType.COMBINED_IMAGE_SAMPLER).build();
    private static @org.jspecify.annotations.Nullable ByteBuffer boundsData;
    private static final String[] SPRITES = {"fire_0", "fire_1", "soul_fire_0", "soul_fire_1"};
    private static long animationTicks;
    private static double animationRemainder;
    private static final int[] frameTops = new int[128];
    private static @org.jspecify.annotations.Nullable GpuBuffer bounds;
    private static @org.jspecify.annotations.Nullable NativeImage image;
    private static @org.jspecify.annotations.Nullable GpuTexture heat, palette;
    private static @org.jspecify.annotations.Nullable GpuTextureView heatView, paletteView;

    private ProceduralFire() { }
    public static void initialize() {
        naturality.client.systems.sleep.SleepVisuals.register(client -> {
            if (heatView == null || client.level == null || client.isPaused()) return;
            animationRemainder += naturality.client.systems.sleep.SleepVisuals.speed();
            long frames = (long)animationRemainder;
            animationRemainder -= frames;
            animationTicks += frames;
            upload();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> close());
    }

    public static void atlasLoaded(Map<Identifier, TextureAtlasSprite> sprites) {
        // Keep the spatial relationships and transparency of the actual exemplars.
        // A luminance-sorted palette discarded precisely the structure we need.
        if (paletteView != null) paletteView.close();
        if (palette != null) palette.close();
        try (var pixels = new NativeImage(64, 512, false)) {
            for (int kind = 0; kind < SPRITES.length; kind++) {
                try (var input = Minecraft.getInstance().getVanillaPackResources().asResourceManager().open(
                        Identifier.withDefaultNamespace("textures/block/" + SPRITES[kind] + ".png"));
                     var source = NativeImage.read(input)) {
                    if (source.getWidth() != 16 || source.getHeight() != 512)
                        throw new IllegalStateException("Unexpected vanilla fire dimensions");
                    for (int y = 0; y < 512; y++) for (int x = 0; x < 16; x++)
                        pixels.setPixel(kind * 16 + x, y, source.getPixel(x, y));
                    for (int frame = 0; frame < 32; frame++) {
                        int top = 15;
                        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
                            if ((source.getPixel(x, frame * 16 + y) >>> 24) > 127) top = Math.min(top, y);
                        frameTops[kind * 32 + frame] = top;
                    }
                } catch (java.io.IOException e) { throw new IllegalStateException("Cannot load vanilla fire exemplars", e); }
            }
            var palette = RenderSystem.getDevice().createTexture(() -> "Naturality vanilla fire exemplars", 5,
                GpuFormat.RGBA8_UNORM, 64, 512, 1, 1);
            ProceduralFire.palette = palette;
            paletteView = RenderSystem.getDevice().createTextureView(palette);
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(palette, pixels, 0, 0, 0, 0);
        }
        ByteBuffer data = ByteBuffer.allocateDirect(80).order(ByteOrder.nativeOrder());
        for (String name : SPRITES) {
            var sprite = sprites.get(Identifier.withDefaultNamespace("block/" + name));
            data.putFloat(sprite == null ? -1 : sprite.getU0()).putFloat(sprite == null ? -1 : sprite.getV0())
                .putFloat(sprite == null ? -1 : sprite.getU1()).putFloat(sprite == null ? -1 : sprite.getV1());
        }
        data.putInt(16).putInt(32).putInt(0).putInt(0).flip();
        boundsData = data;
        if (heatView == null) {

            image = new NativeImage(129, 1, false);
            var heat = RenderSystem.getDevice().createTexture(() -> "Naturality rising fire heat", 5,
                GpuFormat.RGBA8_UNORM, 129, 1, 1, 1);
            ProceduralFire.heat = heat;
            heatView = RenderSystem.getDevice().createTextureView(heat);
        }
        upload();
    }

    private static void upload() {
        var image = ProceduralFire.image;
        var heat = ProceduralFire.heat;
        if (image == null || heat == null) return;
        var spriteData = boundsData;
        if (spriteData != null) {
            spriteData.putInt(72, (int) (animationTicks % 32));
            if (bounds != null) bounds.close();
            bounds = RenderSystem.getDevice().createBuffer(() -> "Naturality fire sprites", 128, spriteData);
        }
        image.setPixel(0, 0, 0xFF000000 | (int) (animationTicks % 32));
        for (int i = 0; i < frameTops.length; i++) image.setPixel(i + 1, 0, 0xFF000000 | frameTops[i]);
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(heat, image, 0, 0, 0, 0);
    }

    public static void bind(RenderPass pass) {
        var bounds = ProceduralFire.bounds;
        if (bounds == null || heatView == null) throw new IllegalStateException("Fire atlas not loaded");
        pass.setUniform("NaturalityFire", bounds);
        pass.setUniform("NaturalityFireHeat", heatView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        pass.setUniform("NaturalityFirePalette", paletteView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
    }
    public static void bindSodium(RenderPass pass) {
        var bounds = ProceduralFire.bounds;
        if (bounds == null || paletteView == null) throw new IllegalStateException("Fire atlas not loaded");
        pass.setUniform("NaturalityFire", bounds);
        pass.setUniform("NaturalityFirePalette", paletteView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
    }
    public static long ticks() { return animationTicks; }
    public static int seedColor(net.minecraft.core.BlockPos pos) {
        long seed = naturality.api.random.SavedRandom.BlockSeed.of(pos.getX(), pos.getY(), pos.getZ());
        seed ^= seed >>> 33; seed *= 0xff51afd7ed558ccdL; seed ^= seed >>> 33;
        // Upper four bits are reserved for per-sheet connection flags.
        return 0xFF000000 | ((int) seed & 0x0FFFFF);
    }
    private static void close() {
        if (bounds != null) { bounds.close(); bounds = null; }
        if (heatView != null) { heatView.close(); heatView = null; }
        if (heat != null) { heat.close(); heat = null; }
        if (paletteView != null) { paletteView.close(); paletteView = null; }
        if (palette != null) { palette.close(); palette = null; }
        if (image != null) { image.close(); image = null; }
    }

    /** Fuel ignites at the base, then diffuses upward and cools; no looping sprite frames. */
    public static final class FireSimulation {
        public static final int WIDTH = 256, HEIGHT = 32;
        private final SplittableRandom random;
        private float[] heat = new float[WIDTH * HEIGHT];
        private float[] next = new float[WIDTH * HEIGHT];
        private final float[] fuel = new float[WIDTH];
        private final float[] target = new float[WIDTH];
        private long ticks;

        public FireSimulation(long seed) {
            random = new SplittableRandom(seed);
        }

        public void tick() {
            for (int x = 0; x < WIDTH; x += 3) {
                if (ticks == 0 || random.nextDouble() < 0.045) {
                    float value = (float) (0.25 + random.nextDouble() * 1.35);
                    for (int dx = 0; dx < 3 && x + dx < WIDTH; dx++) target[x + dx] = value;
                }
            }
            for (int x = 0; x < WIDTH; x++) fuel[x] += (target[x] - fuel[x]) * 0.35F;
            for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
                if (y == 0) {
                    next[x] = fuel[x];
                } else {
                    // Mostly advect, with slight sideways diffusion and wandering wind.
                    // Heavy averaging would erase the tongues into a solid gradient.
                    int drift = random.nextDouble() < 0.12 ? random.nextInt(-1, 2) : 0;
                    float sum = sample(x + drift, y - 1) * 0.96F
                        + (sample(x + drift - 1, y - 1) + sample(x + drift + 1, y - 1)) * 0.02F;
                    next[y * WIDTH + x] = Math.max(0, sum - (float) (0.018 + random.nextDouble() * 0.025));
                }
            }
            float[] old = heat; heat = next; next = old;
            ticks++;
        }

        public float sample(int x, int y) {
            return heat[Math.clamp(y, 0, HEIGHT - 1) * WIDTH + Math.floorMod(x, WIDTH)];
        }
        public long ticks() { return ticks; }
    }

    /** Shared ember-to-firelight color ramp used by lava splash highlights. */
    public static final class FirePalette {
        private static final int[] COLORS={
            0xFF1A0803,0xFF4B1003,0xFF8F1D00,0xFFD83400,
            0xFFF96300,0xFFFF9A08,0xFFFFD12E,0xFFFFF5BA
        };
        private FirePalette() {}
        public static int color(float brightness) {
            float position=Math.clamp(brightness,0,1)*(COLORS.length-1);
            int index=Math.min(COLORS.length-2,(int)position);
            float t=position-index;
            int a=COLORS[index],b=COLORS[index+1];
            int r=Math.round(((a>>>16)&255)*(1-t)+((b>>>16)&255)*t);
            int g=Math.round(((a>>>8)&255)*(1-t)+((b>>>8)&255)*t);
            int blue=Math.round((a&255)*(1-t)+(b&255)*t);
            return 0xFF000000|r<<16|g<<8|blue;
        }
    }
    /** Entity-fire pipeline data; kept separate from model placement and emission. */
    public static final class EntityFireLayer {
        public static final RenderType LAYER = RenderType.create("naturality_entity_fire", RenderSetup.builder(
            RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
                .withLocation(Naturality.id("pipeline/entity_fire"))
                .withVertexShader(Naturality.id("core/fire_fade"))
                .withFragmentShader(Naturality.id("core/entity_fire"))
                .withBindGroupLayout(ProceduralFire.LAYOUT).withCull(false)
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
                .withColorTargetState(ColorTargetState.DEFAULT).build()))
            .withTexture("Sampler0", naturality.client.api.rendering.AtlasLocations.BLOCKS).useLightmap().createRenderSetup());
        private EntityFireLayer() { }
    }
}
