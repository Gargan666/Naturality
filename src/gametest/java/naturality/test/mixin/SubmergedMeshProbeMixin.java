package naturality.test.mixin;

import java.util.concurrent.atomic.AtomicInteger;
import naturality.test.WaterFogOcclusionGameTest;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Checks real Fabric mesh output, not just the face-classification helper. */
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl", remap = false)
public abstract class SubmergedMeshProbeMixin {
    @Shadow private BlockAndTintGetter level;
    @Shadow private BlockPos pos;
    @Shadow private BlockState blockState;

    @Inject(method = "transform", at = @At("RETURN"))
    private void naturality$checkMesh(MutableQuadView quad, CallbackInfoReturnable<Boolean> cir) {
        if (naturality.test.ShortDistanceGameTest.probeShore && cir.getReturnValueZ()
                && level instanceof RenderSectionRegion && blockState.is(Blocks.DIRT)
                && pos.getY() == 63 && pos.getZ() == 28 && pos.getX() >= 3 && pos.getX() <= 16
                && quad.lightFace() == Direction.NORTH) {
            naturality.test.ShortDistanceGameTest.shoreFaces.incrementAndGet();
            for (int i = 0; i < 4; i++) if ((quad.color(i) >>> 24) != 253)
                naturality.test.ShortDistanceGameTest.badShoreVertices.incrementAndGet();
        }
        if (!WaterFogOcclusionGameTest.probe || !cir.getReturnValueZ()
                || !(level instanceof RenderSectionRegion) || pos.getZ() < 16 || pos.getZ() > 79) return;
        AtomicInteger count;
        boolean wet;
        if (blockState.is(Blocks.KELP_PLANT) || blockState.is(Blocks.KELP)) {
            count = WaterFogOcclusionGameTest.plantFaces;
            wet = true;
        } else if (blockState.is(Blocks.SANDSTONE) && pos.getY() == 92 && quad.lightFace() == Direction.UP) {
            count = WaterFogOcclusionGameTest.floorFaces;
            wet = true;
        } else if (blockState.is(Blocks.SMOOTH_QUARTZ) && pos.getY() >= 104) {
            count = WaterFogOcclusionGameTest.dryFaces;
            wet = false;
        } else return;
        count.incrementAndGet();
        for (int i = 0; i < 4; i++) {
            if ((quad.color(i) >>> 24) != (wet ? 254 : 255))
                WaterFogOcclusionGameTest.badFaces.incrementAndGet();
        }
    }
}
