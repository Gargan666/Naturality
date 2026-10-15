package naturality.client.portals.end;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.*;
import naturality.Naturality;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Pattern;
import naturality.client.portals.nether.glow.PortalGlowGeometry.PortalGlowPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.client.particle.SingleQuadParticle;

@org.jspecify.annotations.NullMarked
public final class EndPortalBorderLayers {
    private static final DepthStencilState DEPTH = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);
    public static final RenderType BORDER = create("end_portal_border");

    private static RenderType create(String name) {
        var effect = RenderPipeline.builder()
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER1)
            .withVertexShader(Naturality.id("core/end_portal_wall"))
            .withFragmentShader(Naturality.id("core/end_portal_wall"))
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withCull(false).buildSnippet();
        var pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET, effect)
            .withLocation(Naturality.id("pipeline/" + name)).withDepthStencilState(DEPTH)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
        var oit = RenderPipelines.register(OitPipelineSet.builder("naturality_" + name,
            RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET, RenderPipelines.GLOBALS_SNIPPET, effect))
            .withDepthBoundsModifier(b -> b.withDepthStencilState(DEPTH))
            .withTransmittanceModifier(b -> b.withDepthStencilState(DEPTH))
            .withAccumulateModifier(b -> b.withDepthStencilState(DEPTH)).build());
        return RenderType.create("naturality_" + name, RenderSetup.builder(pipeline)
            .withTexture("Sampler0", naturality.client.api.rendering.AtlasLocations.BLOCKS).useLightmap()
            .withTexture("Sampler1", EndPortalGlowPalette.TEXTURE)
            .setOitPipelines(oit).sortOnUpload().createRenderSetup());
    }

    public static void initialize() { }
    private EndPortalBorderLayers() { }

    /** Bright, fully opaque texels from the nearest star layer, before attenuation. */
    public static final class EndPortalGlowPalette {
        public static final Identifier TEXTURE = Naturality.id("dynamic/end_portal_glow_palette");
        private static @org.jspecify.annotations.Nullable Object loadedModels;
        private static final Pattern TINT = Pattern.compile("vec3\\(\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*\\)");

        public static void prepare(Minecraft client) {
            Object models = client.getModelManager().getBlockStateModelSet();
            if (models == loadedModels) return;
            var colors = new HashSet<Integer>();
            try (var texture = client.getResourceManager().open(Identifier.withDefaultNamespace("textures/entity/end_portal/end_portal.png"));
                 var image = NativeImage.read(texture);
                 var source = client.getResourceManager().open(Naturality.id("shaders/include/end_portal_colors.glsl"))) {
                var matcher = TINT.matcher(new String(source.readAllBytes(), StandardCharsets.UTF_8));
                var tints = new ArrayList<float[]>();
                // Entry 0 is the nearest visible star plane. Deeper planes must not
                // introduce unrelated hues into the border's continuous gradient.
                if (matcher.find()) {
                    float r = Float.parseFloat(matcher.group(1)), g = Float.parseFloat(matcher.group(2)), b = Float.parseFloat(matcher.group(3));
                    float max = Math.max(r, Math.max(g, b));
                    if (max > 0) tints.add(new float[]{r / max, g / max, b / max});
                }
                var texels = new HashSet<Integer>();
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getPixel(x, y);
                    if (ARGB.alpha(pixel) == 255) texels.add(pixel);
                }
                for (int pixel : texels) for (float[] tint : tints) {
                    int r = Math.round(ARGB.red(pixel) * tint[0]);
                    int g = Math.round(ARGB.green(pixel) * tint[1]);
                    int b = Math.round(ARGB.blue(pixel) * tint[2]);
                    // Reject dark source colors instead of manufacturing brightened ones.
                    if (0.2126 * r + 0.7152 * g + 0.0722 * b >= 0.55 * 255)
                        colors.add(ARGB.color(255, r, g, b));
                }
            } catch (IOException | NumberFormatException exception) {
                Naturality.LOGGER.warn("Could not sample bright End portal star colors", exception);
            }
            int[] palette = PortalGlowPalette.create(colors.stream().mapToInt(Integer::intValue).toArray());
            var image = new NativeImage(PortalGlowPalette.SIZE, 1, false);
            for (int i = 0; i < palette.length; i++) image.setPixel(i, 0, palette[i]);
            client.getTextureManager().register(TEXTURE, new DynamicTexture(() -> "Naturality bright End star palette", image));
            loadedModels = models;
        }

        private EndPortalGlowPalette() { }
    }

    /** Independent transient effect; shares only the sampled color texture. */
    public static final class EndEyeGlowLayer {
        private static final DepthStencilState DEPTH = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);
        private static final RenderPipeline.Snippet EFFECT = RenderPipeline.builder()
            .withVertexShader(Naturality.id("core/portal_opening"))
            .withFragmentShader(Naturality.id("core/end_eye_glow"))
            .withCull(false).buildSnippet();
        private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET, EFFECT)
                .withLocation(Naturality.id("pipeline/end_eye_glow"))
                .withDepthStencilState(DEPTH)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
        private static final OitPipelineSet OIT = RenderPipelines.register(
            OitPipelineSet.builder("naturality_end_eye_glow",
                RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET, RenderPipelines.GLOBALS_SNIPPET, EFFECT))
                .withDepthBoundsModifier(b -> b.withDepthStencilState(DEPTH))
                .withTransmittanceModifier(b -> b.withDepthStencilState(DEPTH))
                .withAccumulateModifier(b -> b.withDepthStencilState(DEPTH)).build());
        public static final SingleQuadParticle.Layer LAYER =
            new SingleQuadParticle.Layer(true, EndPortalGlowPalette.TEXTURE, PIPELINE, OIT);
        public static void initialize() { }
        private EndEyeGlowLayer() { }
    }
}
