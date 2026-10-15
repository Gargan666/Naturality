package naturality.client.rendering.shadows;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import naturality.Naturality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/** One texture texel per world 1/16 block, independent of the entity's shadow size. */
public final class BlockGridShadow {
    static final int BORDER = 2;
    public static final int PHASE_SCALE = 16384;
    private static RenderPipeline.Builder pipeline() {
        return RenderPipeline.builder(RenderPipelines.ENTITY_SHADOW_SNIPPET)
            .withVertexShader(Naturality.id("core/entity_shadow"))
            .withFragmentShader(Naturality.id("core/entity_shadow"));
    }
    private static final RenderPipeline PIPELINE = RenderPipelines.register(pipeline()
        .withLocation(Naturality.id("pipeline/entity_shadow"))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
    private static final OitPipelineSet OIT = RenderPipelines.register(
        OitPipelineSet.builder("naturality_entity_shadow", pipeline()).build());
    private static final Identifier SOURCE = Identifier.withDefaultNamespace("textures/misc/shadow.png");
    private static final Map<Integer, RenderType> TYPES = new HashMap<>();
    private static NativeImage source;
    private static boolean failed;

    private BlockGridShadow() { }
    public static void initialize() { }

    public static int pixelRadius(float radius) {
        return Math.max(1, Math.min(512, Math.round(radius * 16)));
    }

    public static RenderType texture(float radius) {
        int pixels = pixelRadius(radius);
        if (failed) return null;
        if (TYPES.containsKey(pixels)) return TYPES.get(pixels);
        var client = Minecraft.getInstance();
        if (source == null) {
            try (var stream = client.getResourceManager().open(SOURCE)) {
                source = NativeImage.read(stream);
            } catch (IOException exception) {
                failed = true;
                Naturality.LOGGER.warn("Cannot generate block-grid entity shadows", exception);
                return null;
            }
        }
        var id = id(pixels);
        client.getTextureManager().register(id, new DynamicTexture(id::toString, generate(source, pixels)));
        var type = RenderType.create("naturality_entity_shadow", RenderSetup.builder(PIPELINE)
            .setOitPipelines(OIT)
            .withTexture("Sampler0", id, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).createRenderSetup());
        TYPES.put(pixels, type);
        return type;
    }

    /** Average a pixel footprint with a soft tent filter, retaining faint diagonal edge coverage. */
    public static NativeImage generate(NativeImage original, int pixels) {
        int diameter = pixels * 2;
        int size = diameter + BORDER * 2;
        var image = new NativeImage(size, size, true);
        for (int z = 1; z < size - 1; z++) for (int x = 1; x < size - 1; x++) {
            double[] sum = new double[4];
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                double weight = (dx == 0 ? 2 : 1) * (dz == 0 ? 2 : 1) / 16.0;
                sample(original, (x - BORDER + 0.5 + dx * 0.75) / diameter,
                    (z - BORDER + 0.5 + dz * 0.75) / diameter, weight, sum);
            }
            int alpha = (int)Math.round(sum[0]);
            if (alpha > 0) image.setPixel(x, z, ARGB.color(alpha,
                (int)Math.round(sum[1] / sum[0]), (int)Math.round(sum[2] / sum[0]),
                (int)Math.round(sum[3] / sum[0])));
        }
        return image;
    }

    /** Bilinear source sampling in premultiplied alpha; transparent pixels cannot tint the rim. */
    private static void sample(NativeImage original, double u, double v, double weight, double[] sum) {
        double sx = u * original.getWidth() - 0.5;
        double sz = v * original.getHeight() - 0.5;
        int ix = (int)Math.floor(sx), iz = (int)Math.floor(sz);
        double fx = sx - ix, fz = sz - iz;
        for (int dz = 0; dz <= 1; dz++) for (int dx = 0; dx <= 1; dx++) {
            int x = ix + dx, z = iz + dz;
            if (x < 0 || z < 0 || x >= original.getWidth() || z >= original.getHeight()) continue;
            int color = original.getPixel(x, z);
            double alpha = ARGB.alpha(color) * weight * (dx == 0 ? 1 - fx : fx) * (dz == 0 ? 1 - fz : fz);
            sum[0] += alpha;
            sum[1] += ARGB.red(color) * alpha;
            sum[2] += ARGB.green(color) * alpha;
            sum[3] += ARGB.blue(color) * alpha;
        }
    }

    /** UV1 carries the subpixel position of integer block origins, never a snapped entity center. */
    public static int gridPhase(float blockRelative) {
        double pixels = blockRelative * 16.0;
        return Math.floorMod((int)Math.round((pixels - Math.floor(pixels)) * PHASE_SCALE), PHASE_SCALE);
    }

    private static Identifier id(int pixels) {
        return Identifier.fromNamespaceAndPath("naturality", "dynamic/entity_shadow/" + pixels);
    }

    public static void reset() {
        var manager = Minecraft.getInstance().getTextureManager();
        for (int pixels : TYPES.keySet()) manager.release(id(pixels));
        TYPES.clear();
        if (source != null) source.close();
        source = null;
        failed = false;
    }
}
