package naturality.client.mixin;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fabric redirects chunk tessellation away from vanilla ModelBlockRenderer. */
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl", remap = false)
public abstract class IndigoSubmergedTerrainMixin {
    @SuppressWarnings("null") @Shadow private BlockAndTintGetter level;
    @SuppressWarnings("null") @Shadow private BlockPos pos;
    @SuppressWarnings("null") @Shadow private BlockState blockState;

    @Inject(method = "shouldCullFace", at = @At("HEAD"), cancellable = true)
    private void naturality$displacedSnowFace(net.minecraft.core.Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (direction == null || !naturality.config.GameplaySettings.clientSnowWrapping()) return;
        var neighbor = pos.relative(direction);
        if (level.getBlockState(neighbor).is(net.minecraft.world.level.block.Blocks.SNOW)
                && !naturality.snow.SnowGeometry.usesVanillaGeometry(level, neighbor)) {
            // State-only occlusion describes snow in its saved cell, not its
            // fitted surfaces. Keep the support face; actual snow depth-occludes it.
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "transform", at = @At(value = "INVOKE",
        target = "Lnet/fabricmc/fabric/impl/client/indigo/renderer/render/AltModelBlockRendererImpl;tintQuad(Lnet/fabricmc/fabric/api/client/renderer/v1/mesh/MutableQuadView;)V",
        shift = At.Shift.AFTER))
    private void naturality$tagWater(MutableQuadView quad, CallbackInfoReturnable<Boolean> cir) {
        if (!(level instanceof RenderSectionRegion)) return;
        var modelOffset = blockState.getOffset(pos);
        int windTag = naturality.client.weather.WindRendering.tag(blockState);
        if (windTag != 255 && blockState.getFluidState().isEmpty()) {
            for (int i = 0; i < 4; i++) {
                int color = quad.color(i);
                int tag = naturality.client.weather.WindRendering.vertexTag(level, pos, blockState, quad.x(i)+(float)modelOffset.x, quad.y(i)+(float)modelOffset.y, quad.z(i)+(float)modelOffset.z);
                if ((color >>> 24) == 255) quad.color(i, (color & 0xFFFFFF) | (tag << 24));
            }
        }
        // Still block-local here: translation into the section happens next.
        var fluidPos = blockState.getFluidState().is(FluidTags.WATER) ? pos : pos.relative(quad.lightFace());
        var fluid = level.getFluidState(fluidPos);
        if (!fluid.is(FluidTags.WATER)) return;
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            float y = pos.getY() + quad.y(i);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        int tag = naturality.client.fluid.SubmergedFace.tag(minY, maxY, fluidPos.getY(), fluid.getHeight(level, fluidPos));
        if (tag == 255) return;
        for (int i = 0; i < 4; i++) {
            int color = quad.color(i);
            int alpha = color >>> 24;
            if (alpha == 255 || alpha >= 64 && alpha <= 117 || alpha >= 118 && alpha <= 192 || alpha >= 201 && alpha <= 240) quad.color(i, (color & 0xFFFFFF) | (tag << 24));
        }
    }
}





