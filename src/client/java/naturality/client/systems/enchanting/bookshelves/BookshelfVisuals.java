package naturality.client.systems.enchanting.bookshelves;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import naturality.Naturality;
import naturality.client.api.rendering.PlacementGlow;
import naturality.client.portals.nether.glow.PortalGlowGeometry.PortalGlowPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.ARGB;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Resource-derived book pixel masks, silhouette edges and palette; placement and HUD live separately. */
@org.jspecify.annotations.NullMarked
public final class BookshelfVisuals {
    private static final Identifier PALETTE = Naturality.id("dynamic/bookshelf_glint_palette");
    private static final RenderPipeline.Snippet GLINT_SHADER = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION).withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.FOG).withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withVertexShader(Naturality.id("core/bookshelf_glint")).withFragmentShader(Naturality.id("core/bookshelf_glint"))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX).withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)).buildSnippet();
    private static final RenderPipeline GLINT_PIPELINE = RenderPipelines.register(RenderPipeline.builder(GLINT_SHADER)
        .withLocation(Naturality.id("pipeline/bookshelf_glint"))
        .withColorTargetState(new ColorTargetState(BlendFunction.GLINT)).build());
    private static final OitPipelineSet GLINT_OIT = RenderPipelines.register(OitPipelineSet.builder("naturality_bookshelf_glint",
        RenderPipeline.builder(GLINT_SHADER).withShaderDefine("OIT_ADDITIVE")).build());
    public static final RenderType GLINT = RenderType.create("naturality_bookshelf_glint", RenderSetup.builder(GLINT_PIPELINE)
        .withTexture("Sampler0", ItemFeatureRenderer.ENCHANTED_GLINT_ITEM)
        // Custom geometry is routed by blending, regardless of the solid-model flag.
        .setOitPipelines(GLINT_OIT).createRenderSetup());
    public static final RenderType GLOW;
    static {
        var glow = PlacementGlow.create("bookshelf_book_glow", true);
        // Both confirmed encoded draws need Iris's scene target. Their programs
        // and native/OIT paths keep the same geometry and authored palette.
        naturality.client.api.rendering.ShaderPacks.registerScenePipeline(GLINT_PIPELINE);
        naturality.client.api.rendering.ShaderPacks.registerScenePipeline(glow.pipeline());
        GLOW = glow.geometry("naturality_bookshelf_book_glow", PALETTE);
    }
    private static final Map<BlockState, List<Slot>> SLOTS = new HashMap<>();
    private static @org.jspecify.annotations.Nullable Object loadedModels;
    public record Pixel(List<Vec3> corners, int x, int y) {}
    public record Edge(Vec3 start, Vec3 end, float pixelSize, float phase) {}
    public record Slot(List<Pixel> pixels, List<Edge> edges) {}
    private BookshelfVisuals() {}
    public static void initialize() {}

    public static List<Slot> slots(Minecraft client, BlockState state) {
        Object models = client.getModelManager().getBlockStateModelSet();
        if (models != loadedModels) {
            SLOTS.clear();
            loadPalette(client);
            loadedModels = models;
        }
        for (var property : ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES) state = state.setValue(property, false);
        return SLOTS.computeIfAbsent(state, empty -> buildSlots(client, empty));
    }

    private static void loadPalette(Minecraft client) {
        int[] colors = new int[0];
        try (var stream = client.getResourceManager().open(ItemFeatureRenderer.ENCHANTED_GLINT_ITEM);
                var source = NativeImage.read(stream)) {
            int[] pixels = new int[source.getWidth() * source.getHeight()];
            for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++)
                pixels[y * source.getWidth() + x] = source.getPixel(x, y);
            colors = pixels;
        } catch (IOException exception) { Naturality.LOGGER.warn("Could not sample enchanted-book glint palette", exception); }
        var palette = PortalGlowPalette.create(colors);
        var image = new NativeImage(PortalGlowPalette.SIZE, 1, false);
        for (int i = 0; i < palette.length; i++) image.setPixel(i, 0, palette[i]);
        client.getTextureManager().register(PALETTE, new DynamicTexture(() -> "Naturality bookshelf glint palette", image));
    }

    private static List<Slot> buildSlots(Minecraft client, BlockState empty) {
        Direction front = empty.getValue(ChiseledBookShelfBlock.FACING);
        var emptyFaces = frontFaces(client, empty, front);
        var images = new HashMap<Identifier, Image>();
        List<Slot> result = new ArrayList<>();
        for (var property : ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES) {
            // Comparing occupied and empty models isolates each actual slot without texture-name assumptions.
            var faces = frontFaces(client, empty.setValue(property, true), front).stream()
                .filter(face -> emptyFaces.stream().noneMatch(other -> sameFace(face, other))).toList();
            var pixels = new ArrayList<Pixel>();
            var edges = new ArrayList<Edge>();
            for (var face : faces) {
                var background = emptyFaces.stream().filter(other -> samePositions(face, other)).findFirst().orElse(null);
                raster(client, face, background, images, pixels, edges);
            }
            result.add(new Slot(List.copyOf(pixels), List.copyOf(edges)));
        }
        return List.copyOf(result);
    }

    private record Image(int width, int height, int[] colors) {
        int at(double x, double y) {
            return colors[Math.clamp((int)Math.floor(y), 0, height - 1) * width + Math.clamp((int)Math.floor(x), 0, width - 1)];
        }
    }

    private static Image image(Minecraft client, BakedQuad face, Map<Identifier, Image> images) {
        var sprite = face.materialInfo().sprite();
        return images.computeIfAbsent(sprite.contents().name(), name -> {
            try (var stream = client.getResourceManager().open(name.withPath("textures/" + name.getPath() + ".png"));
                    var source = NativeImage.read(stream)) {
                int width = sprite.contents().width(), height = sprite.contents().height();
                int[] colors = new int[width * height];
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) colors[y * width + x] = source.getPixel(x, y);
                return new Image(width, height, colors);
            } catch (IOException exception) {
                Naturality.LOGGER.warn("Could not read bookshelf sprite {}", name, exception);
                // Missing source pixels must not turn the entire shelf rectangle into a glowing book.
                return new Image(1, 1, new int[1]);
            }
        });
    }

    private static double texel(BakedQuad face, int vertex, boolean u) {
        var sprite = face.materialInfo().sprite();
        double value = u ? (UVPair.unpackU(face.packedUV(vertex)) - sprite.getU0()) / (sprite.getU1() - sprite.getU0()) * sprite.contents().width()
            : (UVPair.unpackV(face.packedUV(vertex)) - sprite.getV0()) / (sprite.getV1() - sprite.getV0()) * sprite.contents().height();
        // Baked atlas UVs can be slightly inset; restore authored texel boundaries.
        return Math.abs(value - Math.rint(value)) < .02 ? Math.rint(value) : value;
    }

    private static Vec3 point(BakedQuad face, double a, double b) {
        var origin = new Vec3(face.position0());
        return origin.add(new Vec3(face.position1()).subtract(origin).scale(a))
            .add(new Vec3(face.position3()).subtract(origin).scale(b));
    }

    private static void raster(Minecraft client, BakedQuad face, @org.jspecify.annotations.Nullable BakedQuad background,
            Map<Identifier, Image> images, List<Pixel> pixels, List<Edge> edges) {
        var source = image(client, face, images);
        var behind = background == null ? null : image(client, background, images);
        double u = texel(face, 0, true), v = texel(face, 0, false);
        double au = texel(face, 1, true) - u, av = texel(face, 1, false) - v;
        double bu = texel(face, 3, true) - u, bv = texel(face, 3, false) - v;
        double determinant = au * bv - av * bu;
        if (Math.abs(determinant) < 1e-8) return;
        int left = (int)Math.round(Math.min(Math.min(u, u + au), Math.min(u + bu, u + au + bu)));
        int top = (int)Math.round(Math.min(Math.min(v, v + av), Math.min(v + bv, v + av + bv)));
        int width = (int)Math.round(Math.abs(au) + Math.abs(bu)), height = (int)Math.round(Math.abs(av) + Math.abs(bv));
        if (width <= 0 || height <= 0 || (long)width * height > 65536) return;
        var mask = new boolean[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            double dx = left + x + .5 - u, dy = top + y + .5 - v;
            double a = (dx * bv - dy * bu) / determinant, b = (dy * au - dx * av) / determinant;
            if (a < 0 || a > 1 || b < 0 || b > 1) continue;
            int color = source.at(left + x + .5, top + y + .5);
            int old = 0;
            if (background != null && behind != null) {
                double oldU = texel(background, 0, true), oldV = texel(background, 0, false);
                old = behind.at(oldU + a * (texel(background, 1, true) - oldU) + b * (texel(background, 3, true) - oldU),
                    oldV + a * (texel(background, 1, false) - oldV) + b * (texel(background, 3, false) - oldV));
            }
            mask[y * width + x] = ARGB.alpha(color) >= 26 && color != old
                && !shelfShadow(face, source, left + x);
        }
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (!mask[y * width + x]) continue;
            var corners = new ArrayList<Vec3>(4);
            for (int i = 0; i < 4; i++) {
                double dx = left + x + (i == 1 || i == 2 ? 1 : 0) - u;
                double dy = top + y + (i >= 2 ? 1 : 0) - v;
                corners.add(point(face, (dx * bv - dy * bu) / determinant, (dy * au - dx * av) / determinant));
            }
            pixels.add(new Pixel(List.copyOf(corners), left + x, top + y));
            for (int side = 0; side < 4; side++) {
                int nx = x + (side == 1 ? 1 : side == 3 ? -1 : 0), ny = y + (side == 2 ? 1 : side == 0 ? -1 : 0);
                if (nx >= 0 && nx < width && ny >= 0 && ny < height && mask[ny * width + nx]) continue;
                var start = corners.get(side); var end = corners.get((side + 1) % 4);
                float size = (float)start.distanceTo(end);
                edges.add(new Edge(start, end, size, (side % 2 == 0 ? left + x : top + y) * size));
            }
        }
    }

    private static boolean shelfShadow(BakedQuad face, Image source, int x) {
        if (!face.materialInfo().sprite().contents().name().equals(Identifier.withDefaultNamespace("block/chiseled_bookshelf_occupied"))) return false;
        // The vanilla layout's columns 5 and 10 are cast shadows in the gaps, not book spines.
        // Use authored UV regions (also at higher pack resolutions), rather than removing dark book colors.
        double column = (x + .5) * 16 / source.width();
        return column >= 5 && column < 6 || column >= 10 && column < 11;
    }

    private static boolean samePositions(BakedQuad first, BakedQuad second) {
        for (int i = 0; i < 4; i++) if (!first.position(i).equals(second.position(i))) return false;
        return true;
    }

    private static boolean sameFace(BakedQuad first, BakedQuad second) {
        for (int i = 0; i < 4; i++)
            if (!first.position(i).equals(second.position(i)) || first.packedUV(i) != second.packedUV(i)) return false;
        return true;
    }

    private static List<BakedQuad> frontFaces(Minecraft client, BlockState state, Direction front) {
        var parts = new ArrayList<BlockStateModelPart>();
        client.getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(0), parts);
        var faces = new ArrayList<BakedQuad>();
        for (var part : parts) {
            faces.addAll(part.getQuads(front));
            for (var face : part.getQuads(null)) if (face.direction() == front) faces.add(face);
        }
        return faces;
    }
}
