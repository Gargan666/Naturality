package naturality.client.mixin;

import java.util.Map;
import naturality.client.snow.SnowRenderRegion;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSectionRegion.class)
public abstract class SnowRenderRegionMixin implements SnowRenderRegion {
    @Shadow @Final private int minSectionX;
    @Shadow @Final private int minSectionY;
    @Shadow @Final private int minSectionZ;
    @Unique private Map<Long, SectionCopy> naturality$snowSections = Map.of();

    @Override
    public void naturality$setSnowSections(Map<Long, SectionCopy> sections) {
        naturality$snowSections = Map.copyOf(sections);
    }

    @Unique private boolean naturality$outside(BlockPos pos) {
        int x = SectionPos.blockToSectionCoord(pos.getX()) - minSectionX;
        int y = SectionPos.blockToSectionCoord(pos.getY()) - minSectionY;
        int z = SectionPos.blockToSectionCoord(pos.getZ()) - minSectionZ;
        return x < 0 || x >= 3 || y < 0 || y >= 3 || z < 0 || z >= 3;
    }

    @Unique private BlockState naturality$snowState(BlockPos pos) {
        var copy = naturality$snowSections.get(SectionPos.asLong(pos));
        return copy == null ? Blocks.AIR.defaultBlockState() : copy.getBlockState(pos);
    }

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void naturality$block(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (naturality$outside(pos)) cir.setReturnValue(naturality$snowState(pos));
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void naturality$fluid(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (naturality$outside(pos)) cir.setReturnValue(naturality$snowState(pos).getFluidState());
    }

    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void naturality$entity(BlockPos pos, CallbackInfoReturnable<@Nullable BlockEntity> cir) {
        if (!naturality$outside(pos)) return;
        var copy = naturality$snowSections.get(SectionPos.asLong(pos));
        cir.setReturnValue(copy == null ? null : copy.getBlockEntity(pos));
    }
}
