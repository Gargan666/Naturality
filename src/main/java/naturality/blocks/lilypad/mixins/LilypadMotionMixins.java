package naturality.blocks.lilypad.mixins;

import naturality.blocks.lilypad.LilypadMotion;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LilyPadBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class LilypadMotionMixins {
    private LilypadMotionMixins() {}
    @Mixin(Block.class)
    public static abstract class LilypadMotionContactMixin {
        @Inject(method = "stepOn", at = @At("HEAD"))
        private void naturality$weight(Level level, BlockPos pos, BlockState state, Entity entity, CallbackInfo ci) {
            LilypadMotion.step(level, pos, state, entity);
        }
    }
    @Mixin(BlockBehaviour.class)
    public static abstract class LilypadMotionEventMixin {
        @Inject(method = "triggerEvent", at = @At("HEAD"), cancellable = true)
        private void naturality$motion(BlockState state, Level level, BlockPos pos, int event, int data, CallbackInfoReturnable<Boolean> cir) {
            if (event == LilypadMotion.EVENT && state.getBlock() instanceof LilyPadBlock) {
                if (level.isClientSide()) LilypadMotion.receive(level, pos, data != 0);
                cir.setReturnValue(true);
            }
        }
    }
    @Mixin(LilyPadBlock.class)
    public static abstract class LilypadFixedShapeMixin {
        @ModifyReturnValue(method = "getShape", at = @At("RETURN"))
        private VoxelShape naturality$lowerSurface(VoxelShape shape) {
            return shape.move(0, -1.0 / 16, 0);
        }
    }}
