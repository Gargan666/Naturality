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
    private static final AnimationClock CLOCK = new AnimationClock();
    private static final FireSimulation SIMULATION = new FireSimulation(0x46495245);
    private static final int HEAT_ROW = 520;
    private static final int PROFILE_ROW = HEAT_ROW + FireSimulation.HEIGHT;
    private static @org.jspecify.annotations.Nullable NativeImage simulationImage;
    private static long lastFrameNanos;
    private static @org.jspecify.annotations.Nullable GpuBuffer bounds;
    private static @org.jspecify.annotations.Nullable NativeImage image;
    private static @org.jspecify.annotations.Nullable GpuTexture heat, palette;
    private static @org.jspecify.annotations.Nullable GpuTextureView heatView, paletteView;

    private static final int[] ASH_PALETTE = new int[256];
    /** Exact approved flame colors, shared by glowing ash rather than a second invented ramp. */
    public static int ashColor(double heat) { return ASH_PALETTE[Math.clamp((int) Math.round(heat * 255), 0, 255)]; }
    private ProceduralFire() { }
    public static void initialize() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> close());
    }

    /** Once per rendered frame, before any terrain, particles or entities bind the clock. */
    public static void beginFrame() {
        long now = System.nanoTime();
        double elapsed = lastFrameNanos == 0 ? 0 : (now - lastFrameNanos) / 1e9;
        lastFrameNanos = now;
        var client = Minecraft.getInstance();
        var level = client.level;
        if (heatView == null || level == null || client.isPaused()
                || !level.tickRateManager().runsNormally()) {
            CLOCK.reset();
            return;
        }
        if (CLOCK.advance(elapsed, naturality.client.systems.sleep.SleepVisuals.speed())) {
            animationTicks++;
            if (naturality.config.live.NaturalityConfig.get().effects.fireAnimation) {
                SIMULATION.advance();
                uploadSimulation();
            }
        }
        upload();
    }

    /** Nominal vanilla cadence, with no unseen catch-up frames after a slow render. */
    public static final class AnimationClock {
        private double remainder;
        public boolean advance(double seconds, double speed) {
            remainder += Math.clamp(seconds, 0, 0.25) * Math.clamp(speed, 1, 1000) * 20;
            if (remainder < 1 - 1e-9) return false;
            remainder = Math.max(0, remainder - Math.floor(remainder + 1e-9));
            return true;
        }
        public void reset() { remainder = 0; }
        public float fraction() { return (float) remainder; }
    }

    public static void atlasLoaded(Map<Identifier, TextureAtlasSprite> sprites) {
        // Retain the approved exemplars for the disabled-variation fallback and
        // extract colors only. Animated silhouettes come from the heat field.
        if (paletteView != null) paletteView.close();
        if (palette != null) palette.close();
        // Calibrate the periodic field against the actual 32-frame vanilla
        // distribution, separately at each height. Extending the old 16-column
        // bounded simulation into a continuous strip otherwise retains too much
        // heat near its tips and overrepresents pale spots high above the base.
        float[][] referenceHeat = FireSimulation.referenceDistribution();
        try (var pixels = new NativeImage(FireSimulation.WIDTH, PROFILE_ROW + 64, true)) {
            for (int kind = 0; kind < SPRITES.length; kind++) {
                try (var input = Minecraft.getInstance().getVanillaPackResources().asResourceManager().open(
                        Identifier.withDefaultNamespace("textures/block/" + SPRITES[kind] + ".png"));
                     var source = NativeImage.read(input)) {
                    if (source.getWidth() != 16 || source.getHeight() != 512)
                        throw new IllegalStateException("Unexpected vanilla fire dimensions");
                    for (int y = 0; y < 512; y++) for (int x = 0; x < 16; x++)
                        pixels.setPixel(kind * 16 + x, y, source.getPixel(x, y));
                    var colors = new java.util.TreeSet<Integer>();
                    for (int y = 0; y < 512; y++) for (int x = 0; x < 16; x++) {
                        int color = source.getPixel(x, y);
                        if ((color >>> 24) > 127) colors.add(color);
                    }
                    if (colors.isEmpty()) throw new IllegalStateException("Empty fire palette");
                    writeProfile(pixels, source, kind, referenceHeat);
                    var ordered = new java.util.ArrayList<>(colors);
                    ordered.sort(java.util.Comparator.comparingDouble(ProceduralFire::luminance));
                    for (int i = 0; i < 256; i++) {
                        // Selection, never RGB mixing: even intermediate heat uses
                        // an exact authored color. Soul fire retains its own ramp.
                        double intensity = i / 255.0;
                        double red = 100 + 155 * intensity, green = 255 * intensity * intensity;
                        double blue = 255 * Math.pow(intensity, 10);
                        int selected = ordered.getFirst();
                        double best = Double.POSITIVE_INFINITY;
                        double soulLuminance = luminance(ordered.getFirst()) + Math.clamp((intensity * intensity - .25) / .75, 0, 1)
                            * (luminance(ordered.getLast()) - luminance(ordered.getFirst()));
                        for (int color : ordered) {
                            double dr = (color >> 16 & 255) - red, dg = (color >> 8 & 255) - green, db = (color & 255) - blue;
                            double error = kind < 2 ? dr * dr + dg * dg + db * db : Math.abs(luminance(color) - soulLuminance);
                            if (error < best) { best = error; selected = color; }
                        }
                        pixels.setPixel(i, 512 + kind, selected);
                        if (kind == 0) ASH_PALETTE[i] = selected;
                    }
                } catch (java.io.IOException e) { throw new IllegalStateException("Cannot load vanilla fire exemplars", e); }
            }
            var palette = RenderSystem.getDevice().createTexture(() -> "Naturality fire colors and shared heat", 5,
                GpuFormat.RGBA8_UNORM, FireSimulation.WIDTH, PROFILE_ROW + 64, 1, 1);
            ProceduralFire.palette = palette;
            paletteView = RenderSystem.getDevice().createTextureView(palette);
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(palette, pixels, 0, 0, 0, 0);
        }
        if (simulationImage == null) simulationImage = new NativeImage(FireSimulation.WIDTH, FireSimulation.HEIGHT, false);
        if (SIMULATION.ticks() == 0) for (int i = 0; i < 80; i++) SIMULATION.advance();
        uploadSimulation();
        ByteBuffer data = ByteBuffer.allocateDirect(80).order(ByteOrder.nativeOrder());
        for (String name : SPRITES) {
            var sprite = sprites.get(Identifier.withDefaultNamespace("block/" + name));
            data.putFloat(sprite == null ? -1 : sprite.getU0()).putFloat(sprite == null ? -1 : sprite.getV0())
                .putFloat(sprite == null ? -1 : sprite.getU1()).putFloat(sprite == null ? -1 : sprite.getV1());
        }
        data.putInt(16).putInt(32).putInt(0).putInt(0);
        data.flip();
        boundsData = data;
        if (heatView == null) {

            image = new NativeImage(1, 1, false);
            var heat = RenderSystem.getDevice().createTexture(() -> "Naturality rising fire heat", 5,
                GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
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
            spriteData.putInt(76, Math.round(CLOCK.fraction() * 65535));
            if (bounds != null) bounds.close();
            bounds = RenderSystem.getDevice().createBuffer(() -> "Naturality fire sprites", 128, spriteData);
        }
        image.setPixel(0, 0, 0xFF000000 | (int) (animationTicks % 32));
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(heat, image, 0, 0, 0, 0);
    }

    private static double luminance(int color) {
        return .2126 * (color >> 16 & 255) + .7152 * (color >> 8 & 255) + .0722 * (color & 255);
    }

    private static void writeProfile(NativeImage target, NativeImage source, int kind, float[][] field) {
        // Normal fire's red channel inverts its historical 100 + 155*h curve.
        // Soul textures use the same silhouettes; normal heat is the shared
        // physical scale, while their existing color lookup remains separate.
        try (var input = Minecraft.getInstance().getVanillaPackResources().asResourceManager().open(
                Identifier.withDefaultNamespace("textures/block/fire_" + (kind < 2 ? kind : 3 - kind) + ".png"));
             var normal = NativeImage.read(input)) {
            for (int row = 0; row < 16; row++) {
                float[] reference = new float[512];
                for (int frame = 0; frame < 32; frame++) for (int x = 0; x < 16; x++) {
                    int color = source.getPixel(x, frame * 16 + row);
                    int normalColor = normal.getPixel(x, frame * 16 + row);
                    reference[frame * 16 + x] = (color >>> 24) > 127
                        ? Math.clamp(((normalColor >> 16 & 255) - 100) / 155F, .5F, 1) : 0;
                }
                java.util.Arrays.sort(reference);
                int firstOpaque = 0;
                while (firstOpaque < reference.length && reference[firstOpaque] == 0) firstOpaque++;
                int rank = 0;
                for (int i = 0; i < 256; i++) {
                    while (rank < field[row].length && field[row][rank] <= i / 255F) rank++;
                    int index = Math.clamp((int) ((long) rank * reference.length / field[row].length), 0, reference.length - 1);
                    // Store a continuous sub-threshold tail, rather than a jump
                    // from zero to .5, for smooth moving cutout boundaries.
                    float value = reference[index];
                    if (value == 0) {
                        value = firstOpaque == reference.length ? 0 : .499F * index / Math.max(1, firstOpaque);
                    }
                    int encoded = Math.round(value * 255);
                    target.setPixel(i, PROFILE_ROW + kind * 16 + row, 0xFF000000 | encoded << 16);
                }
            }
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot measure vanilla fire heat", e); }
    }

    private static void uploadSimulation() {
        var pixels = simulationImage;
        var texture = palette;
        if (pixels == null || texture == null) return;
        for (int y = 0; y < FireSimulation.HEIGHT; y++) for (int x = 0; x < FireSimulation.WIDTH; x++) {
            // Retain the hot tail for calibration; clipping it to white before
            // measuring its percentile would make excess bright spots permanent.
            int previous = Math.round(Math.clamp(SIMULATION.previous(x, y) * .45F, 0, 1) * 255);
            int current = Math.round(Math.clamp(SIMULATION.sample(x, y) * .45F, 0, 1) * 255);
            pixels.setPixel(x, y, 0xFF000000 | previous << 16 | current << 8);
        }
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, pixels, 0, 0, 0, HEAT_ROW);
    }

    public static void bind(RenderPass pass) {
        var bounds = ProceduralFire.bounds;
        if (bounds == null || heatView == null) throw new IllegalStateException("Fire atlas not loaded");
        pass.setUniform("NaturalityFire", bounds);
        pass.setUniform("NaturalityFireHeat", heatView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        pass.setUniform("NaturalityFirePalette", paletteView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    }
    public static void bindSodium(RenderPass pass) {
        var bounds = ProceduralFire.bounds;
        if (bounds == null || paletteView == null) throw new IllegalStateException("Fire atlas not loaded");
        pass.setUniform("NaturalityFire", bounds);
        pass.setUniform("NaturalityFirePalette", paletteView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    }
    public static long ticks() { return animationTicks; }
    public static int seedColor(net.minecraft.core.BlockPos pos) {
        long seed = naturality.api.random.SavedRandom.BlockSeed.of(pos.getX(), pos.getY(), pos.getZ());
        seed ^= seed >>> 33; seed *= 0xff51afd7ed558ccdL; seed ^= seed >>> 33;
        // Upper four bits encode wall connections; bit 19 marks grouped floor flames.
        return 0xFF000000 | ((int) seed & 0x07FFFF);
    }
    private static void close() {
        if (bounds != null) { bounds.close(); bounds = null; }
        if (heatView != null) { heatView.close(); heatView = null; }
        if (heat != null) { heat.close(); heat = null; }
        if (paletteView != null) { paletteView.close(); paletteView = null; }
        if (palette != null) { palette.close(); palette = null; }
        if (image != null) { image.close(); image = null; }
        if (simulationImage != null) { simulationImage.close(); simulationImage = null; }
    }

    /**
     * Historical 16x20 fire recurrence over a shared periodic strip, reconstructed
     * on a fourfold grid for GPU sampling. Resolution never changes its dynamics:
     * 18*below plus six neighbours, divided by 24*1.06; four hidden fuel rows.
     * We reimplement the equations; colors are selected from current vanilla art.
     * Interpolate the scalar heat, not authored RGB. This preserves the original
     * broad patches instead of introducing extra tiny flames at high resolution.
     * @see <a href="https://github.com/NeRdTheNed/MC-TextureGen/blob/main/mc-texture-gen-impl/src/main/java/com/github/nerdthened/mctexturegen/generators/FireGenerator.java">Reference generator</a>
     */
    public static final class FireSimulation {
        public static final int SCALE = 4, WIDTH = 256, HEIGHT = 20 * SCALE;
        private static final int COLUMNS = WIDTH / SCALE, ROWS = HEIGHT / SCALE;
        private final SplittableRandom random;
        private float[] heat = new float[COLUMNS * ROWS];
        private float[] next = new float[COLUMNS * ROWS];
        private final float[] previous = new float[COLUMNS * ROWS];
        private long ticks;

        public FireSimulation(long seed) {
            random = new SplittableRandom(seed);
        }

        public void tick() {
            for (int y = 0; y < ROWS; y++) for (int x = 0; x < COLUMNS; x++) {
                if (y == 0) {
                    next[x] = (float) (random.nextDouble() * random.nextDouble() * random.nextDouble()
                        * 4 + random.nextDouble() * .1 + .2);
                } else {
                    float sum = cell(heat, x, y - 1) * 18;
                    for (int dx = -1; dx <= 1; dx++) sum += cell(heat, x + dx, y) + cell(heat, x + dx, y - 1);
                    next[y * COLUMNS + x] = sum / 25.44F;
                }
            }
            float[] old = heat; heat = next; next = old;
            ticks++;
        }

        public void advance() {
            System.arraycopy(heat, 0, previous, 0, heat.length);
            tick();
        }

        public float previous(int x, int y) {
            return reconstruct(previous, x, y);
        }

        public float sample(int x, int y) {
            return reconstruct(heat, x, y);
        }
        private static float cell(float[] field, int x, int y) {
            return field[Math.clamp(y, 0, ROWS - 1) * COLUMNS + Math.floorMod(x, COLUMNS)];
        }
        private static float reconstruct(float[] field, int x, int y) {
            int column = Math.floorDiv(x, SCALE), row = Math.floorDiv(y, SCALE);
            float u = Math.floorMod(x, SCALE) / (float) SCALE, v = Math.floorMod(y, SCALE) / (float) SCALE;
            float lower = cell(field, column, row) * (1 - u) + cell(field, column + 1, row) * u;
            float upper = cell(field, column, row + 1) * (1 - u) + cell(field, column + 1, row + 1) * u;
            return lower * (1 - v) + upper * v;
        }
        private static float[][] referenceDistribution() {
            var reference = new FireSimulation(0x56414E494C4C41L);
            for (int i = 0; i < 80; i++) reference.advance();
            float[][] values = new float[16][256 * COLUMNS];
            for (int frame = 0; frame < 256; frame++) {
                reference.advance();
                for (int row = 0; row < 16; row++) for (int x = 0; x < COLUMNS; x++)
                    values[row][frame * COLUMNS + x] = .45F * (
                        cell(reference.heat, x, 19 - row) + cell(reference.previous, x, 19 - row)) * .5F;
            }
            for (float[] row : values) java.util.Arrays.sort(row);
            return values;
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
                .withShaderDefine("NATURALITY_ENTITY_FIRE")
                .withBindGroupLayout(ProceduralFire.LAYOUT).withCull(false)
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
                .withColorTargetState(ColorTargetState.DEFAULT).build()))
            .withTexture("Sampler0", naturality.client.api.rendering.AtlasLocations.BLOCKS).useLightmap().createRenderSetup());
        private EntityFireLayer() { }
    }
}
