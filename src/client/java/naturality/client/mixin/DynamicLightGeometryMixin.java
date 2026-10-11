package naturality.client.mixin;

import naturality.client.lighting.DynamicLighting;
import naturality.client.fluid.WaterVisuals;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class DynamicLightGeometryMixin {
    @Inject(method = "setBlocksDirty", at = @At("HEAD"))
    private void naturality$refreshOcclusion(net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState oldState, net.minecraft.world.level.block.state.BlockState newState,
            CallbackInfo ci) {
        DynamicLighting.invalidate();
        WaterVisuals.invalidateChunk((ClientLevel)(Object)this, pos.getX() >> 4, pos.getZ() >> 4);
    }
    @Inject(method = "onChunkLoaded", at = @At("HEAD"))
    private void naturality$refreshWater(net.minecraft.world.level.ChunkPos pos, CallbackInfo ci) {
        WaterVisuals.invalidateChunkAndNeighbors((ClientLevel)(Object)this, pos.x(), pos.z());
    }
    @Inject(method = "unload", at = @At("HEAD"))
    private void naturality$unloadWaterColumns(net.minecraft.world.level.chunk.LevelChunk chunk, CallbackInfo ci) {
        var pos = chunk.getPos();
        WaterVisuals.invalidateChunkAndNeighbors((ClientLevel)(Object)this, pos.x(), pos.z());
    }
}
