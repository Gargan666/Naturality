package naturality.client.fluids.shared;

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
import java.util.SplittableRandom;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/** One bounded simulation shared by all terrain draws; spatial assembly happens in the shader. */
@org.jspecify.annotations.NullMarked
public final class ProceduralFluids {
    public static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
        .withUniform("NaturalityFluids", UniformType.UNIFORM_BUFFER)
        .withUniform("NaturalityFluidHeat", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("NaturalityLavaPalette", UniformType.COMBINED_IMAGE_SAMPLER).build();
    public static final String[] SPRITES = {"water_still", "water_flow", "lava_still", "lava_flow"};
    private static final FluidSimulation[] simulations = new FluidSimulation[4];
    /** Flowing-water field, stretched along the splash height for vertical streaks. */
    public static float splashWaterHeat(int x,int y) {
        return simulations[1]==null?0.35F:simulations[1].heat(x,Math.floorDiv(y,3));
    }
    private static final NativeImage[] images = new NativeImage[7];
    private static @org.jspecify.annotations.Nullable GpuTexture texture;
    private static @org.jspecify.annotations.Nullable GpuTextureView view;
    private static @org.jspecify.annotations.Nullable GpuTexture palette;
    private static @org.jspecify.annotations.Nullable GpuTextureView paletteView;
    private static @org.jspecify.annotations.Nullable GpuBuffer bounds;
    private static final float[] spriteBounds = new float[16];
    private static int enabledMask = -1;
    private static final int[] paletteCounts = new int[2];
    private static int paletteStride;
    private static java.util.List<Integer> splashLavaPalette=java.util.List.of(0xffb52e08,0xffeb6710,0xffffcd58);
    public static int splashLavaColor(float heat) {
        return splashLavaPalette.get(Math.clamp(Math.round(heat*(splashLavaPalette.size()-1)),0,splashLavaPalette.size()-1));
    }

    private ProceduralFluids() {}

    public static void initialize() {
        naturality.client.systems.sleep.SleepVisuals.register(client -> {
            if (view == null || client.level == null || client.isPaused()) return;
            for (var simulation : simulations) simulation.tick();
            upload();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> close());
    }

    public static void atlasLoaded(Map<Identifier, TextureAtlasSprite> sprites) {
        for (int i = 0; i < SPRITES.length; i++) {
            String name = SPRITES[i];
            var sprite = sprites.get(Identifier.withDefaultNamespace("block/" + name));
            spriteBounds[i * 4] = sprite == null ? -1 : sprite.getU0();
            spriteBounds[i * 4 + 1] = sprite == null ? -1 : sprite.getV0();
            spriteBounds[i * 4 + 2] = sprite == null ? -1 : sprite.getU1();
            spriteBounds[i * 4 + 3] = sprite == null ? -1 : sprite.getV1();
        }
        enabledMask = -1;
        rebuildPalette(sprites);
        refreshBounds();
        if (view != null) return; // Preserve evolving state across resource reloads.
        var random = new SplittableRandom();
        for (int i = 0; i < 4; i++) {
            simulations[i] = new FluidSimulation(i >= 2, (i & 1) != 0, random.nextLong());
            for (int warmup = 0; warmup < 160; warmup++) simulations[i].tick();
        }
        for (int level = 0; level < images.length; level++)
            images[level] = new NativeImage(FluidSimulation.SIZE >> level, FluidSimulation.SIZE >> level, false);
        var texture = RenderSystem.getDevice().createTexture(() -> "Naturality live fluid heat", 5,
            GpuFormat.RGBA8_UNORM, FluidSimulation.SIZE, FluidSimulation.SIZE, 1, images.length);
        ProceduralFluids.texture = texture;
        view = RenderSystem.getDevice().createTextureView(texture);
        upload();
    }

    private static void upload() {
        var texture = ProceduralFluids.texture;
        if (texture == null) return;
        for (int y = 0; y < FluidSimulation.SIZE; y++) for (int x = 0; x < FluidSimulation.SIZE; x++) {
            int r = Math.round(simulations[0].heat(x, y) * 255);
            int g = Math.round(simulations[1].heat(x, y) * 255);
            int b = Math.round(simulations[2].heat(x, y) * 255);
            int a = Math.round(simulations[3].heat(x, y) * 255);
            images[0].setPixel(x, y, a << 24 | r << 16 | g << 8 | b);
        }
        for (int level = 1; level < images.length; level++) {
            var src = images[level - 1];
            var dst = images[level];
            for (int y = 0; y < dst.getHeight(); y++) for (int x = 0; x < dst.getWidth(); x++) {
                int pixel = 0;
                for (int shift = 0; shift < 32; shift += 8) {
                    int sum = 0;
                    for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++)
                        sum += (src.getPixel(x * 2 + dx, y * 2 + dy) >>> shift) & 255;
                    pixel |= ((sum + 2) / 4) << shift;
                }
                dst.setPixel(x, y, pixel);
            }
        }
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        for (int level = 0; level < images.length; level++) encoder.writeToTexture(texture, images[level], level, 0, 0, 0);
    }

    private static void rebuildPalette(Map<Identifier, TextureAtlasSprite> sprites) {
        if (paletteView != null) paletteView.close();
        if (palette != null) palette.close();
        var rows = new java.util.ArrayList<java.util.ArrayList<Integer>>();
        for (int row = 0; row < 2; row++) {
            var colors = new java.util.ArrayList<Integer>();
            // Explicitly use the built-in pack: the requested palette is vanilla,
            // independent of texture packs. Read every pixel, without resampling.
            try (var input = net.minecraft.client.Minecraft.getInstance().getVanillaPackResources()
                    .asResourceManager().open(Identifier.withDefaultNamespace("textures/block/" + SPRITES[row + 2] + ".png"));
                 var source = NativeImage.read(input)) {
                    for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
                        int pixel = source.getPixel(x, y);
                        if ((pixel >>> 24) != 0) colors.add(pixel | 0xFF000000);
                    }
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Cannot read vanilla lava palette", e);
            }
            if (colors.isEmpty()) throw new IllegalStateException("Empty vanilla lava palette");
            colors.sort(java.util.Comparator.comparingInt(color ->
                ((color >>> 16) & 255) * 54 + ((color >>> 8) & 255) * 183 + (color & 255) * 19));
            rows.add(colors);
            if(row==0)splashLavaPalette=java.util.List.copyOf(colors);
            paletteCounts[row] = colors.size();
        }
        paletteStride = (Math.max(paletteCounts[0], paletteCounts[1]) + 255) / 256;
        try (var image = new NativeImage(256, paletteStride * 2, false)) {
            for (int row = 0; row < 2; row++) for (int i = 0; i < paletteStride * 256; i++)
                image.setPixel(i % 256, row * paletteStride + i / 256,
                    rows.get(row).get(Math.min(i, paletteCounts[row] - 1)));
            var palette = RenderSystem.getDevice().createTexture(() -> "Naturality exact vanilla lava palette", 5,
                GpuFormat.RGBA8_UNORM, 256, paletteStride * 2, 1, 1);
            ProceduralFluids.palette = palette;
            paletteView = RenderSystem.getDevice().createTextureView(palette);
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(palette, image, 0, 0, 0, 0);
        }
    }

    public static void bind(RenderPass pass) {
        if (bounds == null || view == null) throw new IllegalStateException("Fluid atlas not loaded");
        refreshBounds();
        var bounds = ProceduralFluids.bounds;
        if (bounds == null) throw new IllegalStateException("Fluid atlas not loaded");
        pass.setUniform("NaturalityFluids", bounds);
        pass.setUniform("NaturalityFluidHeat", view, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST));
        pass.setUniform("NaturalityLavaPalette", paletteView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
    }

    public static long ticks() { return simulations[0] == null ? 0 : simulations[0].ticks(); }

    private static void refreshBounds() {
        var config = naturality.config.live.NaturalityConfig.get().liquids;
        int mask = 0;
        for (int i = 0; i < 4; i++) if (config.includes(i)) mask |= 1 << i;
        if (mask == enabledMask) return;
        ByteBuffer data = ByteBuffer.allocateDirect(96).order(ByteOrder.nativeOrder());
        for (int i = 0; i < 16; i++) data.putFloat(spriteBounds[i]);
        data.putInt(paletteCounts[0]).putInt(paletteCounts[1]).putInt(paletteStride).putInt(mask);
        // Reuse the existing terrain binding: no additional sampler or draw pass.
        naturality.client.blocks.snow.SnowTextureAtlas.writeLodBounds(data);
        data.flip();
        if (bounds != null) bounds.close();
        bounds = RenderSystem.getDevice().createBuffer(() -> "Naturality fluid sprite bounds", 128, data);
        enabledMask = mask;
    }

    private static void close() {
        if (paletteView != null) { paletteView.close(); paletteView = null; }
        if (palette != null) { palette.close(); palette = null; }
        if (bounds != null) { bounds.close(); bounds = null; }
        if (view != null) { view.close(); view = null; }
        if (texture != null) { texture.close(); texture = null; }
        for (int i = 0; i < images.length; i++) if (images[i] != null) { images[i].close(); images[i] = null; }
    }

    /** Live three-layer cellular automata, extended to a larger toroidal domain.
     * Equations: https://github.com/ClassiCube/ClassiCube/wiki/Minecraft-Classic-lava-animation-algorithm
     * Flowing variants use longitudinal stencils and advect the displayed field.
     */
    public static final class FluidSimulation {
        public static final int SIZE = 64;
        private static final int[] DISPLACEMENT = new int[16];
        static {
            for (int i = 0; i < DISPLACEMENT.length; i++)
                DISPLACEMENT[i] = (int) (1.2 * Math.sin(i * Math.PI / 8));
        }
        private final boolean lava;
        private final boolean flowing;
        private final SplittableRandom random;
        private float[] surface = new float[SIZE * SIZE];
        private float[] next = new float[SIZE * SIZE];
        private final float[] reservoir = new float[SIZE * SIZE];
        private final float[] impulse = new float[SIZE * SIZE];
        private long ticks;

        public FluidSimulation(boolean lava, boolean flowing, long seed) {
            this.lava = lava;
            this.flowing = flowing;
            random = new SplittableRandom(seed);
        }

        private static int index(int x, int y) {
            return (y & (SIZE - 1)) * SIZE + (x & (SIZE - 1));
        }

        public void tick() {
            for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                int i = index(x, y);
                float sum = 0;
                if (lava && flowing) {
                    // Upstream-biased transport stretches hot strands along +V, with
                    // only weak cross-stream diffusion. Distinct from still lava's swirl.
                    next[i] = surface[i] * 0.24F
                        + surface[index(x, y - 1)] * 0.16F
                        + surface[index(x, y - 2)] * 0.24F
                        + surface[index(x, y - 3)] * 0.16F
                        + surface[index(x, y + 1)] * 0.04F
                        + (surface[index(x - 1, y - 1)] + surface[index(x + 1, y - 1)]) * 0.02F
                        + (reservoir[i] + reservoir[index(x + 1, y)]
                            + reservoir[index(x, y + 1)] + reservoir[index(x + 1, y + 1)]) * 0.24F;
                } else if (lava) {
                    int cx = x + DISPLACEMENT[y & 15];
                    int cy = y + DISPLACEMENT[x & 15];
                    for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
                        sum += surface[index(cx + dx, cy + dy)];
                    float local = reservoir[i] + reservoir[index(x + 1, y)]
                        + reservoir[index(x, y + 1)] + reservoir[index(x + 1, y + 1)];
                    // Shorter diffusion memory keeps individual cells compact. Raise
                    // the local heat contribution to retain the original mean heat.
                    next[i] = sum / 11 + local * (4F / 11);
                } else if (flowing) {
                    // V is the flow axis in vanilla fluid UVs. Smoothing along it
                    // makes longitudinal streaks instead of transverse falling bars.
                    for (int dy = -2; dy <= 2; dy++) sum += surface[index(x, y + dy)];
                    next[i] = sum / 5.5F + reservoir[i] * 0.8F;
                } else {
                    for (int dx = -1; dx <= 1; dx++) sum += surface[index(x + dx, y)];
                    next[i] = sum / 3.3F + reservoir[i] * 0.8F;
                }
            }
            // Separate passes keep the stencil independent of traversal direction.
            for (int i = 0; i < surface.length; i++) {
                reservoir[i] = Math.max(0, reservoir[i] + impulse[i] * (lava ? 0.01F : 0.05F));
                impulse[i] -= lava ? 0.06F : 0.1F;
                if (random.nextDouble() < (lava ? 0.005 : 0.05)) impulse[i] = lava ? 1.5F : 0.5F;
            }
            float[] previous = surface;
            surface = next;
            next = previous;
            ticks++;
        }

        public float heat(int x, int y) {
            int scroll = flowing ? (int) ((lava ? ticks / 2 : ticks) & (SIZE - 1)) : 0;
            float value = surface[index(x, y - scroll)];
            // Preserve variation in hot regions instead of clipping them into connected
            // white/yellow plateaus. This drives the active lava sprite's color palette.
            return lava ? value / (value + 0.35F) : Math.clamp(value, 0, 1);
        }

        public long ticks() { return ticks; }
    }
}
