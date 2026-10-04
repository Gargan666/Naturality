package naturality.client.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carry face-local water occupancy in the otherwise opaque terrain tint alpha. */
@Mixin(ModelBlockRenderer.class)
public abstract class SubmergedTerrainMixin {
    @SuppressWarnings("null") @Shadow @Final private QuadInstance quadInstance;

    @Inject(method = "putQuadWithTint", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/block/BlockQuadOutput;put(FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"))
    private void naturality$tagWater(BlockQuadOutput output, float x, float y, float z,
            BlockAndTintGetter level, BlockState state, BlockPos pos, BakedQuad quad, CallbackInfo ci) {
        if (!(level instanceof RenderSectionRegion)) return;
        var modelOffset = state.getOffset(pos);
        int windTag = naturality.client.weather.WindRendering.tag(state);
        if (windTag != 255 && state.getFluidState().isEmpty()) {
            for (int i = 0; i < 4; i++) {
                int color = quadInstance.getColor(i);
                int tag = naturality.client.weather.WindRendering.vertexTag(level, pos, state, quad.position(i).x()+(float)modelOffset.x, quad.position(i).y()+(float)modelOffset.y, quad.position(i).z()+(float)modelOffset.z);
                if ((color >>> 24) == 255) quadInstance.setColor(i, (color & 0xFFFFFF) | (tag << 24));
            }
        }
        var fluidPos = state.getFluidState().is(FluidTags.WATER) ? pos : pos.relative(quad.direction());
        var fluid = level.getFluidState(fluidPos);
        if (!fluid.is(FluidTags.WATER)) return;
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            float yPos = pos.getY() + quad.position(i).y();
            minY = Math.min(minY, yPos); maxY = Math.max(maxY, yPos);
        }
        int tag = naturality.client.fluid.SubmergedFace.tag(minY, maxY, fluidPos.getY(), fluid.getHeight(level, fluidPos));
        if (tag == 255) return;
        for (int i = 0; i < 4; i++) {
            int color = quadInstance.getColor(i);
            int alpha = color >>> 24;
            if (alpha == 255 || alpha >= 64 && alpha <= 117 || alpha >= 118 && alpha <= 192 || alpha >= 201 && alpha <= 240) quadInstance.setColor(i, (color & 0xFFFFFF) | (tag << 24));
        }
    }
}





