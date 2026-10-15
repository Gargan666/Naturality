package naturality.client.sky.shared.clouds;

import net.minecraft.client.renderer.*;
import naturality.config.live.NaturalityConfig.CloudLayer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import org.joml.Vector4f;
import java.util.ArrayList;
import net.minecraft.client.renderer.CloudRenderer;

/** Cloud shells with their lighting and sun-occlusion state. */
@org.jspecify.annotations.NullMarked
public final class CloudLayerRenderer implements AutoCloseable {
    private static final int UBO_SIZE = new Std140SizeCalculator().putVec4().putVec3().putVec3().putVec4().putVec4().putVec4().get();
    private boolean needsRebuild = true;
    private int prevCellX = Integer.MIN_VALUE;
    private int prevCellZ = Integer.MIN_VALUE;
    private CloudLayerRenderer.@Nullable TextureData texture;
    private int quadCount = 0;
    private final MappableRingBuffer ubo = new MappableRingBuffer(() -> "Cloud UBO", 130, UBO_SIZE);
    private @Nullable MappableRingBuffer utb;

    private CloudLayer settings = new CloudLayer();
    private int meshKey;
    private int invisibleCaps;
    public void setTexture(net.minecraft.client.renderer.CloudRenderer.@org.jspecify.annotations.Nullable TextureData data) {
        this.texture = data == null ? null : new TextureData(data.cells(), data.width(), data.height());
        this.quadCount = 0;
        this.needsRebuild = true;
    }

    private static int getSizeForCloudDistance(final int radiusCells) {
        int maxFacesPerCell = 6;
        int maxCells = (radiusCells * 2 + 1) * (radiusCells * 2 + 1);
        int maxFaces = maxCells * maxFacesPerCell;
        return maxFaces * 4;
    }

    private static boolean isNorthEmpty(final long cellData) {
        return (cellData >> 3 & 1L) != 0L;
    }

    private static boolean isEastEmpty(final long cellData) {
        return (cellData >> 2 & 1L) != 0L;
    }

    private static boolean isSouthEmpty(final long cellData) {
        return (cellData >> 1 & 1L) != 0L;
    }

    private static boolean isWestEmpty(final long cellData) {
        return (cellData >> 0 & 1L) != 0L;
    }

    public void prepare(
        final int color,
        final CloudStatus cloudStatus,
        final float bottomY,
        int range,
        final Vec3 cameraPosition,
        final long gameTime,
        final float partialTicks, final CloudLayer settings
    ) {
        this.settings = settings;
        if (naturality.client.ui.menus.previews.MenuPreview.active())
            range = Math.max(range * 4, (int)Math.ceil((naturality.client.ui.menus.previews.MenuPreview.radius() * 4 + 180) / 16));
        if (!settings.enabled || settings.opacityPercent == 0) { quadCount = 0; needsRebuild = true; return; }
        float relativeBottomY = (float)(bottomY + settings.heightOffset - cameraPosition.y);
        float cameraHeight = -relativeBottomY / settings.thickness;
        // The menu's level orbit is the shared middle for both cloud layers.
        // Playable worlds retain their actual camera-height transitions.
        if (naturality.client.ui.menus.previews.MenuPreview.active())
            cameraHeight = naturality.client.ui.menus.previews.MenuPreview.MenuWorld.camera().cloudLayerPosition();
        boolean gradient = settings.fadingSides && settings.style != 2 && settings.fadePixels >= 2;
        // The fragment shader discards these caps at exactly zero opacity. Keep
        // a small margin at the transition so rounding can never hide a visible cap.
        int caps = gradient ? (cameraHeight <= .9999F ? 1 : 0) | (cameraHeight >= .0001F ? 2 : 0) : 0;
        if (caps != invisibleCaps) { invisibleCaps = caps; needsRebuild = true; }
        int key = 31 * (31 * (31 * (31 * settings.width + settings.thickness) + settings.style)
            + (settings.fadingSides ? 1 : 0)) + range;
        if (key != meshKey) { meshKey = key; needsRebuild = true; }
        var texture = this.texture;
        if (texture != null) {
            int radiusBlocks = range * 16;
            int radiusCells = Math.min(240, Mth.ceil(radiusBlocks / (float)settings.width));
            int utbSize = getSizeForCloudDistance(radiusCells);
            if (this.utb == null || this.utb.currentBuffer().size() != utbSize) {
                if (this.utb != null) {
                    this.utb.close();
                }

                this.utb = new MappableRingBuffer(() -> "Naturality cloud faces", 258, utbSize);
                this.needsRebuild = true;
            }

            var faces = java.util.Objects.requireNonNull(this.utb);
            // Double precision avoids drift jumps far from spawn and after long sessions.
            double cloudOffset = (gameTime + (double)partialTicks) * 0.03 * settings.speedPercent / 100.0;
            double cloudX = cameraPosition.x + settings.offsetX + cloudOffset;
            double cloudZ = cameraPosition.z + settings.offsetZ + 3.96;
            double textureWidthBlocks = texture.width * (double)settings.width;
            double textureHeightBlocks = texture.height * (double)settings.width;
            cloudX -= Math.floor(cloudX / textureWidthBlocks) * textureWidthBlocks;
            cloudZ -= Math.floor(cloudZ / textureHeightBlocks) * textureHeightBlocks;
            int cellX = Mth.floor(cloudX / settings.width);
            int cellZ = Mth.floor(cloudZ / settings.width);
            float xInCell = (float)(cloudX - cellX * settings.width);
            float zInCell = (float)(cloudZ - cellZ * settings.width);
            boolean fancyClouds = settings.style != 2;
            if (this.needsRebuild
                || cellX != this.prevCellX
                || cellZ != this.prevCellZ) {
                this.needsRebuild = false;
                this.prevCellX = cellX;
                this.prevCellZ = cellZ;
                faces.rotate();

                try (GpuBufferSlice.MappedView view = faces.currentBuffer().map(false, true)) {
                    this.buildMesh(view.data(), cellX, cellZ, fancyClouds, radiusCells);
                    this.quadCount = view.data().position() / 4;
                }
            }

            if (this.quadCount != 0) {
                RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                indices.requestIndexCount(6 * this.quadCount);

                try (GpuBufferSlice.MappedView view = this.ubo.currentBuffer().map(false, true)) {
                    // Preserve the live sky tint, replacing vanilla base alpha with the layer opacity.
                    Std140Builder.intoBuffer(view.data()).putVec4(ARGB.vector4fFromARGB32(color).setComponent(3, settings.opacityPercent / 100.0f)).putVec3(-xInCell, relativeBottomY, -zInCell).putVec3(settings.width, settings.thickness, settings.width)
                        .putVec4(settings.fadingSides && settings.style != 2 ? settings.fadePixels : 0,
                            cameraHeight, cellX, cellZ)
                        .putVec4(CloudMoonLighting.sample(partialTicks))
                        .putVec4(texture.width, texture.height, settings.fadePixels, naturality.client.sky.overworld.bloodmoon.BloodMoonClient.sample(partialTicks));
                }
            }
        }
    }

    public void renderDepth(RenderPass pass) {
        if (texture == null || quadCount == 0) return;
        RenderSystem.bindDefaultUniforms(pass);
        render(pass, CloudPipelines.DEPTH_ONLY);
    }

    public void renderOitDepth(GpuTextureView sceneDepth, OitRenderPassProvider.Parameters params, boolean copyDepth) {
        try (var pass = OitRenderPassProvider.createRenderPass(OitStage.DEPTH_BOUNDS, () -> "Cloud occlusion", params)) {
            if (copyDepth) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.BLIT_DEPTH_DURING_DEPTH_BOUNDS));
                pass.setUniform("InSampler", sceneDepth, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.draw(3, 1, 0, 0);
            }
            if (texture != null && quadCount != 0) render(pass, CloudPipelines.OIT_DEPTH_ONLY);
        }
    }

    public void render(final CloudStatus cloudStatus, final RenderPass renderPass) {
        if (this.texture != null && this.quadCount != 0) {
            RenderPipeline renderPipeline = settings.style != 2 ? CloudPipelines.VOLUME : CloudPipelines.FLAT;
            renderPass.pushDebugGroup(() -> "Clouds");
            RenderSystem.bindDefaultUniforms(renderPass);
            this.render(renderPass, renderPipeline);
            renderPass.popDebugGroup();
        }
    }

    public void renderOit(
        final CloudStatus cloudStatus, final OitStage stage, final GpuTextureView mainDepthTextureView, final OitRenderPassProvider.Parameters params
    ) {
        if (this.texture != null && this.quadCount != 0) {
            RenderPipeline renderPipeline = (settings.style != 2 ? CloudPipelines.OIT_VOLUME : CloudPipelines.OIT_FLAT).getPipeline(stage);

            try (RenderPass renderPass = OitRenderPassProvider.createRenderPass(stage, () -> "Clouds", params)) {
                this.render(renderPass, renderPipeline);
            }
        }
    }

    private void render(final RenderPass renderPass, final RenderPipeline renderPipeline) {
        var faces = this.utb;
        if (faces == null || quadCount == 0) return;
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer();
        renderPass.setPipeline(RenderSystem.getCompiledPipeline(renderPipeline));
        renderPass.setUniform("DynamicTransforms", dynamicTransforms);
        renderPass.setIndexBuffer(indexBuffer, indices.type());
        renderPass.setVertexBuffer(0, null);
        renderPass.setUniform("CloudInfo", this.ubo.currentBuffer());
        naturality.client.ui.menus.previews.MenuSurface.MenuFog.bind(renderPass);
        renderPass.setUniform("CloudFaces", faces.currentBuffer());
        renderPass.drawIndexed(6 * this.quadCount, 1, 0, 0, 0);
    }

    private void buildMesh(
        final ByteBuffer faceBuffer,
        final int centerCellX,
        final int centerCellZ,
        final boolean extrude,
        final int radiusCells
    ) {
        var texture = this.texture;
        if (texture != null) {
            long[] cells = texture.cells;
            int textureWidth = texture.width;
            int textureHeight = texture.height;

            for (int ring = 2 * radiusCells; ring >= 0; ring--) {
                for (int relativeCellX = -ring; relativeCellX <= ring; relativeCellX++) {
                    int relativeCellZ = ring - Math.abs(relativeCellX);
                    if (relativeCellZ >= 0 && relativeCellZ <= radiusCells && relativeCellX * relativeCellX + relativeCellZ * relativeCellZ <= radiusCells * radiusCells) {
                        if (relativeCellZ != 0) {
                            this.tryBuildCell(faceBuffer, centerCellX, centerCellZ, extrude, relativeCellX, textureWidth, -relativeCellZ, textureHeight, cells);
                        }

                        this.tryBuildCell(faceBuffer, centerCellX, centerCellZ, extrude, relativeCellX, textureWidth, relativeCellZ, textureHeight, cells);
                    }
                }
            }
        }
    }

    private void tryBuildCell(
        final ByteBuffer faceBuffer,
        final int cellX,
        final int cellZ,
        final boolean extrude,
        final int relativeCellX,
        final int textureWidth,
        final int relativeCellZ,
        final int textureHeight,
        final long[] cells
    ) {
        int indexX = Math.floorMod(cellX + relativeCellX, textureWidth);
        int indexY = Math.floorMod(cellZ + relativeCellZ, textureHeight);
        long cellData = cells[indexX + indexY * textureWidth];
        if (cellData != 0L) {
            int corners = 0;
            if (cells[Math.floorMod(indexX - 1, textureWidth) + Math.floorMod(indexY - 1, textureHeight) * textureWidth] == 0) corners |= 16;
            if (cells[Math.floorMod(indexX + 1, textureWidth) + Math.floorMod(indexY - 1, textureHeight) * textureWidth] == 0) corners |= 32;
            if (cells[Math.floorMod(indexX + 1, textureWidth) + Math.floorMod(indexY + 1, textureHeight) * textureWidth] == 0) corners |= 64;
            if (cells[Math.floorMod(indexX - 1, textureWidth) + Math.floorMod(indexY + 1, textureHeight) * textureWidth] == 0) corners |= 128;
            if (extrude) {
                this.buildExtrudedCell(faceBuffer, relativeCellX, relativeCellZ, cellData, corners);
            } else {
                this.encodeFace(faceBuffer, relativeCellX, relativeCellZ, Direction.DOWN, 32 | ((cellData & 15L) != 0 ? 8 : 0) | (((int)(cellData & 15L) | corners) << 8));
            }
        }
    }

    private void encodeFace(final ByteBuffer faceBuffer, final int x, final int z, final Direction direction, final int flags) {
        if (settings.style == 0 && !settings.fadingSides && direction == Direction.UP) return;
        if (settings.fadingSides && settings.style != 2 && settings.fadePixels >= 2) {
            if (direction == Direction.UP && (invisibleCaps & 1) != 0
                    || direction == Direction.DOWN && (invisibleCaps & 2) != 0) return;
            // The camera stays inside cell (0,0); gradient walls are already
            // discarded when back-facing. Retain their original draw ordering.
            if (!naturality.client.ui.menus.previews.MenuPreview.active()
                    && (direction == Direction.NORTH && z <= 0 || direction == Direction.SOUTH && z >= 0
                    || direction == Direction.WEST && x <= 0 || direction == Direction.EAST && x >= 0)) return;
        }
        int dirAndFlags = direction.get3DDataValue() | flags;
        dirAndFlags |= (x & 1) << 7;
        dirAndFlags |= (z & 1) << 6;
        faceBuffer.put((byte)(x >> 1)).put((byte)(z >> 1)).put((byte)dirAndFlags).put((byte)(flags >> 8));
    }

    private void buildExtrudedCell(final ByteBuffer faceBuffer, final int x, final int z, final long cellData, final int corners) {
        int edge = (((int)(cellData & 15L) | corners) << 8) | ((cellData & 15L) != 0 ? 8 : 0);
        this.encodeFace(faceBuffer, x, z, Direction.UP, edge);
        this.encodeFace(faceBuffer, x, z, Direction.DOWN, edge);

        if (isNorthEmpty(cellData)) {
            this.encodeFace(faceBuffer, x, z, Direction.NORTH, edge);
        }

        if (isSouthEmpty(cellData)) {
            this.encodeFace(faceBuffer, x, z, Direction.SOUTH, edge);
        }

        if (isWestEmpty(cellData)) {
            this.encodeFace(faceBuffer, x, z, Direction.WEST, edge);
        }

        if (isEastEmpty(cellData)) {
            this.encodeFace(faceBuffer, x, z, Direction.EAST, edge);
        }


    }

    public void markForRebuild() {
        this.needsRebuild = true;
    }

    public void endFrame() {
        this.ubo.rotate();
    }

    @Override
    public void close() {
        this.ubo.close();
        if (this.utb != null) {
            this.utb.close();
        }
    }

    public record TextureData(long[] cells, int width, int height) {
    }

    public static final class CloudMoonLighting {
        private static final org.joml.Vector3f renderedDirection = new org.joml.Vector3f();
        private static boolean captured;
        public static void beginSky() { captured = false; }
        public static void capture(com.mojang.blaze3d.vertex.PoseStack pose) {
            pose.last().pose().transformDirection(new org.joml.Vector3f(0, 1, 0), renderedDirection);
            com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrixCopy().transformDirection(renderedDirection);
            renderedDirection.normalize();
            captured = true;
        }
        public static float phaseStrength(int phase) {
            return 0.1F + 0.9F * (float)((1 + Math.cos(phase * Math.PI / 4)) * 0.5);
        }

        public static org.joml.Vector3f direction(float angle) {
            var pose = new com.mojang.blaze3d.vertex.PoseStack();
            pose.rotateDegrees(com.mojang.math.Axis.YP, -90.0F);
            pose.rotate(com.mojang.math.Axis.XP, angle);
            return pose.last().pose().transformDirection(new org.joml.Vector3f(0, 1, 0));
        }

        public static Vector4f sample(float partialTicks) {
            var client = Minecraft.getInstance();
            var level = client.level;
            if (level == null || !level.dimension().equals(Level.OVERWORLD)) return new Vector4f();
            var probe = client.gameRenderer.mainCamera().attributeProbe();
            float moon = probe.getValue(EnvironmentAttributes.MOON_ANGLE, partialTicks) * (float)(Math.PI / 180);
            float sun = probe.getValue(EnvironmentAttributes.SUN_ANGLE, partialTicks) * (float)(Math.PI / 180);
            int phase = probe.getValue(EnvironmentAttributes.MOON_PHASE, partialTicks).index();
            float night = Math.clamp(-(float)Math.cos(sun) * 3, 0, 1);
            night = night * night * (3 - 2 * night);
            float strength = night * phaseStrength(phase) * Math.clamp((float)Math.cos(moon) * 4, 0, 1)
                * (1 - level.getRainLevel(partialTicks) * 0.8F);
            var viewDirection = captured ? new org.joml.Vector3f(renderedDirection)
                : com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrixCopy().transformDirection(direction(moon));
            return new Vector4f(viewDirection, strength);
        }

        private CloudMoonLighting() { }
    }

    public static final class CloudSunOcclusion {
        private record Layer(CloudRenderer.TextureData texture, CloudLayer settings,
                double bottom, double drift, double range) {}
        private static final ArrayList<Layer> LAYERS = new ArrayList<>(2);
        private CloudSunOcclusion() {}
        public static void beginFrame() { LAYERS.clear(); }
        public static void add(CloudRenderer.TextureData texture, CloudLayer settings,
                float bottom, int range, long time, float partial) {
            if (settings.enabled && settings.opacityPercent > 0)
                LAYERS.add(new Layer(texture, settings, bottom + settings.heightOffset,
                    (time + (double) partial) * 0.03 * settings.speedPercent / 100.0, range * 16.0));
        }

        public static float transmission(Vec3 eye, Vec3 end) {
            float result = 1;
            for (var layer : LAYERS) result *= 1 - coverage(layer, eye, end.subtract(eye).normalize());
            return result;
        }

        private static float coverage(Layer layer, Vec3 eye, Vec3 ray) {
            var c = layer.settings;
            double top = layer.bottom + (c.style == 2 ? 0 : c.thickness);
            double start, finish;
            if (Math.abs(ray.y) < 1.0E-8) {
                if (eye.y < layer.bottom || eye.y > top || c.style == 2) return 0;
                start = 0; finish = layer.range * 2;
            } else {
                double a = (layer.bottom - eye.y) / ray.y, b = (top - eye.y) / ray.y;
                start = Math.max(0, Math.min(a, b)); finish = Math.max(a, b);
                if (finish < start) return 0;
            }
            double offsetX = c.offsetX + layer.drift, offsetZ = c.offsetZ + 3.96;
            // Traverse each crossed cloud cell, including side intersections at low sun angles.
            double t = start;
            float covered = 0;
            for (int step = 0; step < 1024 && t <= finish; step++) {
                double x = eye.x + ray.x * t + offsetX, z = eye.z + ray.z * t + offsetZ;
                int cx = (int) Math.floor(x / c.width), cz = (int) Math.floor(z / c.width);
                double horizontal = Math.max(Math.abs(ray.x * t), Math.abs(ray.z * t));
                if (horizontal > layer.range + c.width) break;
                var tex = layer.texture;
                if (tex.cells()[Math.floorMod(cx, tex.width()) + Math.floorMod(cz, tex.height()) * tex.width()] != 0) {
                    float fog = (float) Math.max(0, 1 - t / layer.range);
                    covered = Math.max(covered, c.opacityPercent / 100F * fog);
                }
                if (c.style == 2 || t == finish) break;
                double dx = Math.abs(ray.x) < 1.0E-8 ? Double.POSITIVE_INFINITY
                    : ((ray.x > 0 ? cx + 1 : cx) * c.width - x) / ray.x;
                double dz = Math.abs(ray.z) < 1.0E-8 ? Double.POSITIVE_INFINITY
                    : ((ray.z > 0 ? cz + 1 : cz) * c.width - z) / ray.z;
                double next = t + Math.min(dx, dz) + 1.0E-5;
                if (next > finish) break;
                t = next;
            }
            return covered;
        }
    }
}
