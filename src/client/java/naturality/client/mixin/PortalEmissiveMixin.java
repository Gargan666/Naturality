package naturality.client.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Full-bright portal faces without changing block light propagation or portal mechanics. */
@Mixin(BlockModelLighter.class)
public abstract class PortalEmissiveMixin {
    @Inject(method = "prepareQuadFlat", at = @At("HEAD"), cancellable = true)
    private void naturality$emissiveFlat(BlockAndTintGetter level, BlockState state, BlockPos pos,
            int light, BakedQuad quad, QuadInstance output, CallbackInfo ci) {
        naturality$fullBright(state, output, ci);
    }

    @Inject(method = "prepareQuadAmbientOcclusion", at = @At("HEAD"), cancellable = true)
    private void naturality$emissiveAmbient(BlockAndTintGetter level, BlockState state, BlockPos pos,
            BakedQuad quad, QuadInstance output, CallbackInfo ci) {
        naturality$fullBright(state, output, ci);
    }

    private static void naturality$fullBright(BlockState state, QuadInstance output, CallbackInfo ci) {
        if (naturality.config.NaturalityConfig.get().portalChanges.portalBlockChanges && state.is(Blocks.NETHER_PORTAL)) {
            output.setLightCoords(0xF000F0);
            output.setColor(0xFFFFFFFF);
            ci.cancel();
        }
    }
}
