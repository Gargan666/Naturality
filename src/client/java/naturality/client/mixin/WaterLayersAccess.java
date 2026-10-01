package naturality.client.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;

@Mixin(ChunkSectionsToRender.class)
public interface WaterLayersAccess {
    @Accessor("terrainTransformUBO")
    @org.jspecify.annotations.Nullable GpuBufferSlice naturality$terrainTransform();

    @Invoker("renderLayers")
    void naturality$renderWaterMask(ChunkSectionLayer[] layers, GpuSampler sampler, RenderPass pass,
        GpuTextureView atlas, GpuTextureView lightmap, RenderPipeline pipeline, RenderPipeline multidraw);
}
