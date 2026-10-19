package naturality.client.api.rendering.layers.mixins;

import naturality.client.api.rendering.layers.SurfaceLayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Refresh every section whose coating mesh depends on an edited owner, support or side occluder. */
public final class LayerMixins {
    private LayerMixins() {}

    @Mixin(LevelExtractor.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class LayerOverlayUpdatesMixin {
        @Shadow private @Nullable ClientLevel level;

        @Invoker("setSectionDirty")
        protected abstract void naturality$dirtyLayerSection(int x, int y, int z, boolean playerChanged);

        @Inject(method = "setBlockDirty(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("TAIL"))
        private void naturality$dependentOverlays(BlockPos pos, BlockState before, BlockState after, CallbackInfo ci) {
            ClientLevel current = level;
            if (current == null) return;
            SurfaceLayerModel.Updates.changed(current,pos,before,after,
                (x,y,z) -> naturality$dirtyLayerSection(x,y,z,true));
        }
    }
}
