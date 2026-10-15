package naturality.client.portals.nether.glow;

import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.Arrays;
import java.util.Comparator;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import naturality.Naturality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;

/** Fixed frame geometry; local Y follows portal depth, local Z points inward. */
@org.jspecify.annotations.NullMarked
public final class PortalGlowGeometry {
    public static final double HALO_OFFSET = 1.0 / 512.0;
    // The rays must begin in front of the halo, not pass through its plane.
    public static final double RAY_ROOT_OFFSET = HALO_OFFSET + 1.0 / 1024.0;

    // Bounded surface-normal separation: avoids coplanar depth fighting without
    // a slope-dependent camera-depth bias that can penetrate other blocks.
    public static final double INSET = 1.0 / 1024.0;
    public static final int SLICES_PER_BLOCK = 16;
    // +/- 1.5 pixels gives a three-pixel peak-to-peak wave.
    public static final float WAVE_AMPLITUDE = 1.5F / 16.0F;
    public static final float BASE_REACH = 0.875F;
    public static final float PORTAL_SURFACE = 0.125F;
    public static final double FACE_EPSILON = 0.0001;
    private static final Direction[] X_EDGES = {
        Direction.DOWN, Direction.UP, Direction.WEST, Direction.EAST
    };
    private static final Direction[] Z_EDGES = {
        Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH
    };

    private PortalGlowGeometry() { }

    /** Clip the rear glow at the portal slab, independently of translucent draw order. */
    public static int visibleDepthSide(double cameraDepth) {
        if (cameraDepth > PORTAL_SURFACE) return 1;
        if (cameraDepth < -PORTAL_SURFACE) return -1;
        return 0;
    }

    public static float expoOut(float progress) {
        if (progress <= 0) return 0;
        if (progress >= 1) return 1;
        return (float) (1 - Math.pow(2, -10 * progress));
    }

    public static Direction[] edges(Direction.Axis axis) {
        return (axis == Direction.Axis.X ? X_EDGES : Z_EDGES).clone();
    }

    public static Vector3f depth(Direction.Axis axis) {
        return axis == Direction.Axis.X ? new Vector3f(0, 0, 1) : new Vector3f(1, 0, 0);
    }

    public static Quaternionf rotation(Direction.Axis axis, Direction inward) {
        Vector3f normal = new Vector3f(inward.getStepX(), inward.getStepY(), inward.getStepZ());
        Vector3f depth = depth(axis);
        Vector3f tangent = new Vector3f(depth).cross(normal);
        return new Quaternionf().setFromNormalized(new Matrix3f(tangent, depth, normal));
    }

    public static boolean isFront(double cameraX, double cameraY, double cameraZ,
                                  double faceX, double faceY, double faceZ, Direction inward) {
        return (cameraX - faceX) * inward.getStepX()
            + (cameraY - faceY) * inward.getStepY()
            + (cameraZ - faceZ) * inward.getStepZ() > FACE_EPSILON;
    }

    /** A light-to-dark lookup table containing only colors present in the portal. */
    public static final class PortalGlowPalette {
        public static final int SIZE = 256;
        private PortalGlowPalette() { }

        public static int[] create(int[] pixels) {
            Integer[] colors = Arrays.stream(pixels).filter(pixel -> (pixel >>> 24) != 0)
                .map(pixel -> pixel | 0xFF000000).distinct().boxed()
                .sorted(Comparator.<Integer>comparingDouble(PortalGlowPalette::luminance)
                    .reversed().thenComparingInt(Integer::intValue)).toArray(Integer[]::new);
            int[] result = new int[SIZE];
            if (colors.length == 0) return result;
            for (int i = 0; i < SIZE; i++) {
                result[i] = colors[(int) Math.round(i * (colors.length - 1.0) / (SIZE - 1))];
            }
            return result;
        }

        private static double luminance(int color) {
            return 0.2126 * linear((color >>> 16) & 255)
                + 0.7152 * linear((color >>> 8) & 255) + 0.0722 * linear(color & 255);
        }

        private static double linear(int channel) {
            double value = channel / 255.0;
            return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }
    }

    /** Opacity and nearest-filtered palette of the active portal PNG. */
    public record PortalGlowAppearance(float opacity) {
        public static final Identifier PALETTE_TEXTURE = Naturality.id("dynamic/portal_glow_palette");
        public static PortalGlowAppearance load(Minecraft client) {
            Identifier texture = Identifier.withDefaultNamespace("textures/block/nether_portal.png");
            try (InputStream stream = client.getResourceManager().open(texture);
                 NativeImage image = NativeImage.read(stream)) {
                int[] pixels = new int[image.getWidth() * image.getHeight()];
                double weight = 0;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int pixel = image.getPixel(x, y);
                        int alpha = ARGB.alpha(pixel);
                        pixels[y * image.getWidth() + x] = pixel;
                        weight += alpha;
                    }
                }
                upload(client, PortalGlowPalette.create(pixels));
                return new PortalGlowAppearance((float) (weight / pixels.length / 255));
            } catch (IOException exception) {
                Naturality.LOGGER.warn("Could not sample nether portal palette; using fallback", exception);
            }
            upload(client, PortalGlowPalette.create(new int[] {0xFFCC66FF, 0xFF661ACC, 0xFF220044}));
            return new PortalGlowAppearance(0.75F);
        }

        private static void upload(Minecraft client, int[] colors) {
            NativeImage image = new NativeImage(PortalGlowPalette.SIZE, 1, false);
            for (int i = 0; i < colors.length; i++) image.setPixel(i, 0, colors[i]);
            // TextureManager closes the previous texture at this ID on resource reload.
            client.getTextureManager().register(PALETTE_TEXTURE,
                new DynamicTexture(() -> "Naturality portal glow palette", image));
        }
    }

    public static final class PortalGlowRenderLayer {
        // Geometry has a tiny fixed separation from its supporting frame face.
        // Use unbiased depth in every pass so foreground blocks still occlude it.
        private static final DepthStencilState DEPTH =
            new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);
        private static final RenderPipeline.Snippet EFFECT = RenderPipeline.builder()
            .withVertexShader(Naturality.id("core/portal_opening"))
            .withFragmentShader(Naturality.id("core/portal_glow"))
            .withShaderDefine("GLOW_SLICES", PortalGlowGeometry.SLICES_PER_BLOCK)
            .withShaderDefine("GLOW_WAVE_AMPLITUDE", PortalGlowGeometry.WAVE_AMPLITUDE)
            .withShaderDefine("GLOW_BASE_REACH", PortalGlowGeometry.BASE_REACH)
            .withShaderDefine("GLOW_PORTAL_SURFACE", PortalGlowGeometry.PORTAL_SURFACE)
            .buildSnippet();
        private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET, EFFECT)
                .withLocation(Naturality.id("pipeline/portal_glow"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(DEPTH)
                .build());
        private static final OitPipelineSet OIT = RenderPipelines.register(
            OitPipelineSet.builder("naturality_portal_glow",
                    RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET, RenderPipelines.GLOBALS_SNIPPET, EFFECT))
                .withDepthBoundsModifier(builder -> builder.withDepthStencilState(DEPTH))
                .withTransmittanceModifier(builder -> builder.withDepthStencilState(DEPTH))
                .withAccumulateModifier(builder -> builder.withDepthStencilState(DEPTH))
                .build());
        public static final SingleQuadParticle.Layer LAYER =
            new SingleQuadParticle.Layer(true, PortalGlowAppearance.PALETTE_TEXTURE, PIPELINE, OIT);

        private static final RenderPipeline MOTE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
                .withLocation(Naturality.id("pipeline/portal_mote"))
                .withVertexShader(Naturality.id("core/portal_opening"))
                .withFragmentShader(Naturality.id("core/portal_mote"))
                .withCull(false)
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
        private static final OitPipelineSet MOTE_OIT = RenderPipelines.register(
            OitPipelineSet.builder("naturality_portal_mote",
                RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET)
                    .withVertexShader(Naturality.id("core/portal_opening"))
                    .withFragmentShader(Naturality.id("core/portal_mote")).withCull(false)).build());
        public static final SingleQuadParticle.Layer MOTE_LAYER = new SingleQuadParticle.Layer(
            true, PortalGlowAppearance.PALETTE_TEXTURE, MOTE_PIPELINE, MOTE_OIT);

        private PortalGlowRenderLayer() { }

        public static void initialize() {
            // Eager registration before the game's shader resource reload.
        }
    }

    public static final class PortalMoteGeometry {
        private PortalMoteGeometry() { }

        public static double spawnOffset(double normalOffset) {
            return Math.copySign(Math.max(Math.abs(normalOffset),
                PortalGlowGeometry.PORTAL_SURFACE + 0.3001), normalOffset);
        }

        public static float arrivalAlpha(double distance) {
            return (float) Math.clamp(distance / 0.1, 0, 1);
        }

        public static float crossWidth(float proximity) {
            return 0.25F + 0.75F * Math.clamp(proximity, 0, 1);
        }

        public static float paletteU(float proximity) {
            int index = Math.round((1 - Math.clamp(proximity, 0, 1)) * (PortalGlowPalette.SIZE - 1));
            return (index + 0.5F) / PortalGlowPalette.SIZE;
        }

        public static float growth(double distance, double initialDistance) {
            double progress = Math.clamp(1 - distance / Math.max(initialDistance, 0.0001), 0, 1);
            return (float) (progress * progress * (3 - 2 * progress));
        }

    }

    /** Active resource-pack luminance range, stable across animation frames, refreshed on atlas reload. */
    public static final class PortalTextureBrightness {
        private static @org.jspecify.annotations.Nullable TextureAtlasSprite currentSprite;
        private static int packedRange = 32767 << 16;

        public static int packedRange(Minecraft client) {
            var sprite = client.getAtlasManager().get(new SpriteId(naturality.client.api.rendering.AtlasLocations.BLOCKS,
                Identifier.withDefaultNamespace("block/nether_portal")));
            if (sprite == currentSprite) return packedRange;
            currentSprite = sprite;
            packedRange = 32767 << 16;
            try (var stream = client.getResourceManager().open(Identifier.withDefaultNamespace("textures/block/nether_portal.png"));
                 var image = NativeImage.read(stream)) {
                float min = 1, max = 0;
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getPixel(x, y);
                    if (ARGB.alpha(pixel) == 0) continue;
                    float luminance = (0.2126F * ARGB.red(pixel) + 0.7152F * ARGB.green(pixel) + 0.0722F * ARGB.blue(pixel)) / 255;
                    min = Math.min(min, luminance);
                    max = Math.max(max, luminance);
                }
                if (max >= min) {
                    // Inward rounding ensures both texture endpoints clamp to exactly 0/1.
                    int low = (int) Math.ceil(min * 32767), high = (int) Math.floor(max * 32767);
                    packedRange = low | Math.max(low, high) << 16;
                }
            } catch (IOException e) {
                Naturality.LOGGER.warn("Could not sample portal reveal brightness range", e);
            }
            return packedRange;
        }
        private PortalTextureBrightness() { }
    }
}
