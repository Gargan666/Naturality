package naturality.client.rendering.biometint;

import java.util.Set;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

/** A common world-space color field for biome-tinted models and fluid vertices. */
@org.jspecify.annotations.NullMarked
public final class BiomeTint {
    private BiomeTint() {}

    /** Retain the native item/particle contracts; only terrain vertices use the field. */
    public record Source(BlockTintSource original) implements BlockTintSource {
        @Override public int color(BlockState state) { return original.color(state); }
        @Override public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            return original.colorInWorld(state, level, pos);
        }
        @Override public int colorAsTerrainParticle(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            return original.colorAsTerrainParticle(state, level, pos);
        }
        @Override public Set<Property<?>> relevantProperties() { return original.relevantProperties(); }
    }

    public static @Nullable Source source(BlockColors colors, BlockState state, int index) {
        var sources = colors.getTintSources(state);
        return index >= 0 && index < sources.size() && sources.get(index) instanceof Source source ? source : null;
    }

    /** Coordinates are relative to pos, before section translation or animated wind. */
    public static int at(Source source, BlockAndTintGetter level, BlockState state, BlockPos pos,
            double x, double y, double z) {
        x -= 0.5; y -= 0.5; z -= 0.5;
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        double fx = x - bx, fy = y - by, fz = z - bz;
        double red = 0, green = 0, blue = 0;
        for (int dz = 0; dz < 2; dz++) for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
            double weight = (dx == 0 ? 1 - fx : fx) * (dy == 0 ? 1 - fy : fy) * (dz == 0 ? 1 - fz : fz);
            if (weight == 0) continue;
            int color = source.colorInWorld(state, level, pos.offset(bx + dx, by + dy, bz + dz));
            red += ARGB.red(color) * weight;
            green += ARGB.green(color) * weight;
            blue += ARGB.blue(color) * weight;
        }
        return ARGB.color((int) Math.round(red), (int) Math.round(green), (int) Math.round(blue));
    }

    /** Fluid input RGB already contains cardinal shading; alpha carries flow metadata. */
    public static int replaceFluidTint(int shaded, int original, int replacement) {
        int channel = ARGB.red(original) >= ARGB.green(original) ? 16 : 8;
        if (ARGB.blue(original) > ((original >>> channel) & 255)) channel = 0;
        int denominator = (original >>> channel) & 255;
        float shade = denominator == 0 ? 1 : (float) ((shaded >>> channel) & 255) / denominator;
        return (shaded & 0xff000000) | (ARGB.scaleRGB(replacement, shade) & 0xffffff);
    }
}
