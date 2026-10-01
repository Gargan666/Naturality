package naturality.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightEngine.class)
public abstract class SnowLightEngineMixin {
    @WrapOperation(method="getState", at=@At(value="INVOKE", target="Lnet/minecraft/world/level/chunk/LightChunk;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState naturality$snowLight(LightChunk chunk, BlockPos pos, Operation<BlockState> original) {
        return naturality.snow.SnowLighting.state(chunk,pos,original.call(chunk,pos));
    }
}
