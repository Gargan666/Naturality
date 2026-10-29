package naturality.client.blocks.glass;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import naturality.client.blocks.snow.SnowTextureAtlas.SnowSpritePixels;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/** Sprite bounds, texture-pixel dimensions and brightest visible colors for native glass shine. */
@org.jspecify.annotations.NullMarked
public final class GlassShine {
    public static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
        .withUniform("NaturalityGlass", UniformType.UNIFORM_BUFFER).build();
    private static final int CAPACITY = 34;
    private static @Nullable GpuBuffer bounds;

    private GlassShine() {}

    public static boolean isGlass(Identifier id) {
        if (!id.getNamespace().equals("minecraft")) return false;
        String path = id.getPath();
        if (path.equals("block/glass") || path.equals("block/glass_pane_top")) return true;
        if (!path.startsWith("block/")) return false;
        int end;
        if (path.endsWith("_stained_glass_pane_top")) end = path.length() - "_stained_glass_pane_top".length();
        else if (path.endsWith("_stained_glass")) end = path.length() - "_stained_glass".length();
        else return false;
        if (end <= "block/".length()) return false;
        // This predicate runs for every terrain quad. Preserve [a-z_]+ without a Pattern/Matcher per face.
        for (int i = "block/".length(); i < end; i++) {
            char c = path.charAt(i);
            if (c != '_' && (c < 'a' || c > 'z')) return false;
        }
        return true;
    }

    public static void loaded(Map<Identifier, TextureAtlasSprite> sprites) {
        close();
        var data = ByteBuffer.allocateDirect(CAPACITY * 48).order(ByteOrder.nativeOrder());
        for (int i = 0; i < CAPACITY * 12; i++) data.putFloat(-1);
        int index = 0;
        for (var entry : sprites.entrySet()) {
            if (!isGlass(entry.getKey())) continue;
            if (index == CAPACITY) break;
            var sprite = entry.getValue();
            data.position(index * 16);
            data.putFloat(sprite.getU0()).putFloat(sprite.getV0()).putFloat(sprite.getU1()).putFloat(sprite.getV1());
            data.position(CAPACITY * 16 + index * 16);
            data.putFloat(sprite.contents().width()).putFloat(sprite.contents().height()).putFloat(0).putFloat(0);
            int color = brightestColor(((SnowSpritePixels)sprite.contents()).naturality$image());
            data.position(CAPACITY * 32 + index * 16);
            data.putFloat(ARGB.red(color) / 255F).putFloat(ARGB.green(color) / 255F)
                .putFloat(ARGB.blue(color) / 255F).putFloat(1);
            index++;
        }
        data.clear();
        bounds = RenderSystem.getDevice().createBuffer(() -> "Naturality glass sprites", 128, data);
    }

    /** Hidden RGB in alpha-zero texels is not a visible texture color. Scan all
     * animation frames so a resource pack's palette stays stable during playback. */
    private static int brightestColor(NativeImage image) {
        int brightest = 0;
        double maximum = -1;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int color = image.getPixel(x, y);
            if (ARGB.alpha(color) == 0) continue;
            double luminance = ARGB.red(color) * 0.2126 + ARGB.green(color) * 0.7152 + ARGB.blue(color) * 0.0722;
            if (luminance > maximum) { maximum = luminance; brightest = color; }
        }
        return brightest;
    }

    public static void bind(RenderPass pass) {
        var buffer = bounds;
        if (buffer == null) throw new IllegalStateException("Glass atlas not loaded");
        pass.setUniform("NaturalityGlass", buffer);
    }

    public static void close() {
        if (bounds != null) { bounds.close(); bounds = null; }
    }
}
