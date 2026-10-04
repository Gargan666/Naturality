package naturality.mixin;

import naturality.snow.SnowGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(Level.class)
public abstract class SnowStateMixin {
    @WrapMethod(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z")
    private boolean naturality$snowChange(BlockPos pos, BlockState state, int flags, int limit, Operation<Boolean> original) {
        Level level = (Level)(Object)this;
        if (!naturality.config.GameplaySettings.snowWrapping(level)) return original.call(pos, state, flags, limit);
        // Every support allows at least seven layers; only an eighth needs geometry resolution.
        if (state.is(Blocks.SNOW) && state.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS)==8) {
            int maximum=SnowGeometry.maxLayers(level,pos);
            if(maximum<8) state=state.setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS,maximum);
        }
        boolean wasSnow = level.getBlockState(pos).is(Blocks.SNOW);
        // Move the saved snow before neighbor updates can destroy it as unsupported.
        // Keep the layer count (including overflow blocks), rather than dropping
        // snow items and trying to reconstruct the coating afterward.
        java.util.List<BlockState> snowColumn = java.util.List.of();
        if (!level.isClientSide() && state.isAir() && level.getBlockState(pos.above()).is(Blocks.SNOW)
                && SnowGeometry.exposesGround(level,pos)
                && level.getBlockState(pos.above()).canSurvive(level,pos)) {
            snowColumn = new java.util.ArrayList<>();
            for (int i=1;i<=SnowGeometry.MAX_DEPTH;i++) {
                var snow=level.getBlockState(pos.above(i));
                if(!snow.is(Blocks.SNOW))break;
                snowColumn.add(snow);
            }
            // Suppress shape updates until the column is seated at its new origin.
            for(int i=0;i<snowColumn.size();i++)
                level.setBlock(pos.above(i+1),Blocks.AIR.defaultBlockState(),18);
        }
        boolean result = original.call(pos, snowColumn.isEmpty()?state:snowColumn.getFirst(), flags, limit);
        if(!snowColumn.isEmpty()) {
            if(result) {
                for(int i=1;i<snowColumn.size();i++)level.setBlock(pos.above(i),snowColumn.get(i),flags);
            } else {
                for(int i=0;i<snowColumn.size();i++)level.setBlock(pos.above(i+1),snowColumn.get(i),flags);
            }
        }
        if (result && !level.isClientSide() && (wasSnow || state.is(Blocks.SNOW)
                || level.getBlockState(pos.above()).is(Blocks.SNOW))) SnowGeometry.refreshGrass(level, pos);
        return result;
    }
}

