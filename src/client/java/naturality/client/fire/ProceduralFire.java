package naturality.client.fire;

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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

public final class ProceduralFire {
    public static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
        .withUniform("NaturalityFire", UniformType.UNIFORM_BUFFER)
        .withUniform("NaturalityFireHeat", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("NaturalityFirePalette", UniformType.COMBINED_IMAGE_SAMPLER).build();
    private static final String[] SPRITES = {"fire_0", "fire_1", "soul_fire_0", "soul_fire_1"};
    private static long animationTicks;
    private static final int[] frameTops = new int[128];
    private static @org.jspecify.annotations.Nullable GpuBuffer bounds;
    private static @org.jspecify.annotations.Nullable NativeImage image;
    private static @org.jspecify.annotations.Nullable GpuTexture heat, palette;
    private static @org.jspecify.annotations.Nullable GpuTextureView heatView, paletteView;

    private ProceduralFire() { }
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (heatView == null || client.level == null || client.isPaused()) return;
            animationTicks++;
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
        if (bounds != null) bounds.close();
        bounds = RenderSystem.getDevice().createBuffer(() -> "Naturality fire sprites", 128, data);
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
    public static long ticks() { return animationTicks; }
    public static int seedColor(net.minecraft.core.BlockPos pos) {
        long seed = naturality.util.BlockSeed.of(pos.getX(), pos.getY(), pos.getZ());
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
}


