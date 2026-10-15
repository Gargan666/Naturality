package naturality.client.mixin;

import naturality.client.menu.MenuPreview;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native post-processing deltas must not repopulate the discarded underground columns. */
@Mixin(LevelChunk.class)
public abstract class MenuSurfaceChunkMixin {
    @Unique private int @org.jspecify.annotations.Nullable [] naturality$surfaceBottom;
    @Inject(method = "replaceWithPacketData", at = @At("TAIL"))
    private void naturality$rememberShell(int x, int z, ClientboundLevelChunkPacketData data, CallbackInfo ci) {
        if (!MenuPreview.active()) return;
        var chunk = (LevelChunk)(Object)this;
        naturality.client.menu.MenuFog.invalidate(chunk);
        var bottomByColumn = new int[256];
        naturality$surfaceBottom = bottomByColumn;
        for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) {
            int bottom = chunk.getMaxY() + 1;
            for (int i = 0; i < chunk.getSections().length && bottom > chunk.getMaxY(); i++) {
                var section = chunk.getSection(i);
                if (section.hasOnlyAir()) continue;
                for (int dy = 0; dy < 16; dy++) if (!section.getBlockState(dx, dy, dz).isAir()) {
                    bottom = (chunk.getSectionYFromSectionIndex(i) << 4) + dy;
                    break;
                }
            }
            bottomByColumn[dz * 16 + dx] = bottom;
        }
    }
    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void naturality$rejectBuriedDelta(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        var bottomByColumn = naturality$surfaceBottom;
        if (bottomByColumn != null && MenuPreview.active()
                && pos.getY() < bottomByColumn[(pos.getZ() & 15) * 16 + (pos.getX() & 15)])
            cir.setReturnValue(((LevelChunk)(Object)this).getBlockState(pos));
    }
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void naturality$refreshSurface(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        var chunk = (LevelChunk)(Object)this;
        if (MenuPreview.active() && chunk.getLevel().isClientSide()) naturality.client.menu.MenuFog.invalidate(chunk);
    }
}
