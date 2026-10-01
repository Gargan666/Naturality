package naturality.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkSkyLightSources.class)
public abstract class SnowSkySourcesMixin {
    @WrapOperation(method={"update","findLowestSourceBelow"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState naturality$updatedSnow(BlockGetter level, BlockPos pos, Operation<BlockState> original) {
        return naturality.snow.SnowLighting.state(level,pos,original.call(level,pos));
    }
    @WrapOperation(method="findLowestSourceY", at=@At(value="INVOKE", target="Lnet/minecraft/world/level/chunk/LevelChunkSection;getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState naturality$initialSnow(LevelChunkSection section, int x, int y, int z, Operation<BlockState> original,
            @Local(argsOnly=true) ChunkAccess chunk, @Local(ordinal=1) BlockPos.MutableBlockPos bottomPos) {
        var pos=new BlockPos(chunk.getPos().getMinBlockX()+x,bottomPos.getY(),chunk.getPos().getMinBlockZ()+z);
        return naturality.snow.SnowLighting.state(chunk,pos,original.call(section,x,y,z));
    }
}
