package naturality.client.cloud;
import net.minecraft.client.renderer.*;
import naturality.config.NaturalityConfig.CloudLayer;

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

/** Compact cloud shells using the active resource pack's occupancy grid. */
public final class CloudLayerRenderer implements AutoCloseable {
    private static final int UBO_SIZE = new Std140SizeCalculator().putVec4().putVec3().putVec3().putVec4().get();
    private boolean needsRebuild = true;
    private int prevCellX = Integer.MIN_VALUE;
    private int prevCellZ = Integer.MIN_VALUE;
    private CloudLayerRenderer.@Nullable TextureData texture;
    private int quadCount = 0;
    private final MappableRingBuffer ubo = new MappableRingBuffer(() -> "Cloud UBO", 130, UBO_SIZE);
    private @Nullable MappableRingBuffer utb;

    private CloudLayer settings = new CloudLayer();
    private int meshKey;
    public void setTexture(net.minecraft.client.renderer.CloudRenderer.@org.jspecify.annotations.Nullable TextureData data) {
        this.texture = data == null ? null : new TextureData(data.cells(), data.width(), data.height());
        this.quadCount = 0;
        this.needsRebuild = true;
    }

    private static int getSizeForCloudDistance(final int radiusCells) {
        int maxFacesPerCell = 6;
        int maxCells = (radiusCells * 2 + 1) * (radiusCells * 2 + 1);
        int maxFaces = maxCells * maxFacesPerCell;
        return maxFaces * 3;
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
        final int range,
        final Vec3 cameraPosition,
        final long gameTime,
        final float partialTicks, final CloudLayer settings
    ) {
        this.settings = settings;
        if (!settings.enabled || settings.opacityPercent == 0) { quadCount = 0; needsRebuild = true; return; }
        int key = java.util.Objects.hash(settings.width, settings.thickness, settings.style, settings.fadingSides, range);
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
            float relativeBottomY = (float)(bottomY + settings.heightOffset - cameraPosition.y);
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
                    this.quadCount = view.data().position() / 3;
                }
            }

            if (this.quadCount != 0) {
                RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                indices.requestIndexCount(6 * this.quadCount);

                try (GpuBufferSlice.MappedView view = this.ubo.currentBuffer().map(false, true)) {
                    // Preserve the live sky tint, replacing vanilla base alpha with the layer opacity.
                    Std140Builder.intoBuffer(view.data()).putVec4(ARGB.vector4fFromARGB32(color).setComponent(3, settings.opacityPercent / 100.0f)).putVec3(-xInCell, relativeBottomY, -zInCell).putVec3(settings.width, settings.thickness, settings.width)
                        .putVec4(settings.fadingSides && settings.style != 2 ? settings.fadePixels : 0,
                            -relativeBottomY / settings.thickness, 0, 0);
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
            if (extrude) {
                this.buildExtrudedCell(faceBuffer, relativeCellX, relativeCellZ, cellData);
            } else {
                this.buildFlatCell(faceBuffer, relativeCellX, relativeCellZ);
            }
        }
    }

    private void buildFlatCell(final ByteBuffer faceBuffer, final int x, final int z) {
        this.encodeFace(faceBuffer, x, z, Direction.DOWN, 32);
    }

    private void encodeFace(final ByteBuffer faceBuffer, final int x, final int z, final Direction direction, final int flags) {
        if (settings.style == 0 && !settings.fadingSides && direction == Direction.UP) return;
        int dirAndFlags = direction.get3DDataValue() | flags;
        dirAndFlags |= (x & 1) << 7;
        dirAndFlags |= (z & 1) << 6;
        faceBuffer.put((byte)(x >> 1)).put((byte)(z >> 1)).put((byte)dirAndFlags);
    }

    private void buildExtrudedCell(final ByteBuffer faceBuffer, final int x, final int z, final long cellData) {
        this.encodeFace(faceBuffer, x, z, Direction.UP, 0);
        this.encodeFace(faceBuffer, x, z, Direction.DOWN, 0);

        if (isNorthEmpty(cellData)) {
            this.encodeFace(faceBuffer, x, z, Direction.NORTH, 0);
        }

        if (isSouthEmpty(cellData)) {
            this.encodeFace(faceBuffer, x, z, Direction.SOUTH, 0);
        }

        if (isWestEmpty(cellData)) {
            this.encodeFace(faceBuffer, x, z, Direction.WEST, 0);
        }

        if (isEastEmpty(cellData)) {
            this.encodeFace(faceBuffer, x, z, Direction.EAST, 0);
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
}
