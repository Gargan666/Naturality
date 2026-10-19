package naturality.client.rendering.lighting.dynamic.mixins;

import naturality.client.rendering.lighting.dynamic.DynamicLighting;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import naturality.client.fluids.water.WaterVisuals;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.lighting.LightEngine;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class DynamicMixins {
    private DynamicMixins() {}

    @Mixin(BlockLightEngine.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class DynamicBlockLightMixin {
        @Inject(method = "getEmission", at = @At("RETURN"), cancellable = true)
        private void naturality$dynamicEmission(long position, BlockState block, CallbackInfoReturnable<Integer> cir) {
            cir.setReturnValue(Math.max(cir.getReturnValueI(), DynamicLighting.emission(this, position)));
        }
    }

    @Mixin(LightCoordsUtil.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class DynamicLightCoordsMixin {
        @Inject(method = "getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
        private static void naturality$fractionalLight(LightCoordsUtil.BrightnessGetter brightnessGetter,
                BlockAndLightGetter level, BlockState block, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
            if (DynamicLighting.sourceCount() == 0 || cir.getReturnValueI() == LightCoordsUtil.FULL_BRIGHT) return;
            // Preview worlds and other mods may implement brightness without a real
            // light engine. Only touch the client world and its terrain snapshots.
            if (!(level instanceof net.minecraft.client.multiplayer.ClientLevel)
                    && !(level instanceof net.minecraft.client.renderer.chunk.RenderSectionRegion)) return;
            int extra = DynamicLighting.smoothLight(level.getLightEngine().getLayerListener(LightLayer.BLOCK), pos.asLong());
            if (extra > LightCoordsUtil.smoothBlock(cir.getReturnValueI()))
                cir.setReturnValue(LightCoordsUtil.smoothPack(extra, LightCoordsUtil.smoothSky(cir.getReturnValueI())));
        }
    }

    @Mixin(MultiPlayerGameMode.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class DynamicLightDropMixin {
        @Inject(method = "dropItem", at = @At("HEAD"))
        private void naturality$bridgeDrop(LocalPlayer player, boolean all, CallbackInfo ci) {
            DynamicLighting.beginDrop(player, player.getMainHandItem());
        }
    }

    @Mixin(ClientLevel.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class DynamicLightGeometryMixin {
        @Inject(method = "setBlocksDirty", at = @At("HEAD"))
        private void naturality$refreshOcclusion(net.minecraft.core.BlockPos pos,
                net.minecraft.world.level.block.state.BlockState oldState, net.minecraft.world.level.block.state.BlockState newState,
                CallbackInfo ci) {
            DynamicLighting.invalidate(pos);
            naturality.client.rendering.culling.FogCulling.OcclusionCulling.changed(pos);
            WaterVisuals.invalidateChunk((ClientLevel)(Object)this, pos.getX() >> 4, pos.getZ() >> 4);
        }
        @Inject(method = "onChunkLoaded", at = @At("HEAD"))
        private void naturality$refreshWater(net.minecraft.world.level.ChunkPos pos, CallbackInfo ci) {
            naturality.client.rendering.culling.FogCulling.OcclusionCulling.chunkLoaded((ClientLevel)(Object)this, pos);
            WaterVisuals.invalidateChunkAndNeighbors((ClientLevel)(Object)this, pos.x(), pos.z());
        }
        @Inject(method = "unload", at = @At("HEAD"))
        private void naturality$unloadWaterColumns(net.minecraft.world.level.chunk.LevelChunk chunk, CallbackInfo ci) {
            var pos = chunk.getPos();
            WaterVisuals.invalidateChunkAndNeighbors((ClientLevel)(Object)this, pos.x(), pos.z());
        }
    }

    @Mixin(LightEngine.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class DynamicLightStorageMixin {
        @ModifyReturnValue(method = "getLightValue", at = @At("RETURN"))
        private int naturality$virtualField(int light, BlockPos position) {
            // Empty high sections have no vanilla storage. The identity check also
            // excludes skylight and every server/world engine from this overlay.
            // Compose with optical skylight instead of cancelling other overlays.
            return Math.max(light, DynamicLighting.blockLight(this, position.asLong()));
        }
    }

    @org.spongepowered.asm.mixin.Pseudo
    @Mixin(targets = "ca.spottedleaf.starlight.common.light.StarLightInterface", remap = false)
    public static abstract class ScalableLuxDynamicLightMixin {
        @SuppressWarnings("null") // Initialized by ScalableLux's constructor.
        @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
        protected net.minecraft.world.level.lighting.LayerLightEventListener blockReader;

        @ModifyReturnValue(method = "getBlockLightValue", at = @At("RETURN"))
        private int naturality$virtualField(int light, BlockPos pos,
                net.minecraft.world.level.chunk.@org.jspecify.annotations.Nullable ChunkAccess chunk) {
            // Includes direct raw-brightness queries, not just LayerLightEventListener.
            // Reader identity confines the overlay to the active client block layer.
            return Math.max(light, DynamicLighting.blockLight(blockReader, pos.asLong()));
        }
    }

    @org.spongepowered.asm.mixin.Pseudo
    @Mixin(targets = "ca.spottedleaf.starlight.common.light.StarLightInterface$2", remap = false)
    public static abstract class ScalableLuxDynamicLightDataMixin {
        @Inject(method = "getDataLayerData", at = @At("RETURN"), cancellable = true)
        private void naturality$virtualSnapshot(net.minecraft.core.SectionPos pos,
                CallbackInfoReturnable<net.minecraft.world.level.chunk.DataLayer> cir) {
            cir.setReturnValue(DynamicLighting.data(this, pos, cir.getReturnValue()));
        }
    }
}
