package naturality.blocks.banner.mixins;

import naturality.blocks.banner.BannerPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;

public final class BannerMixins {
    private BannerMixins() {}

    @Mixin(BlockGetter.class)
    public interface Picking {
        @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "clip")
        private net.minecraft.world.phys.BlockHitResult naturality$context(net.minecraft.world.level.ClipContext context,
                Operation<net.minecraft.world.phys.BlockHitResult> original) {
            var previous = BannerPlacement.PICK.get();
            BannerPlacement.PICK.set(context);
            try { return original.call(context); }
            finally { if (previous == null) BannerPlacement.PICK.remove(); else BannerPlacement.PICK.set(previous); }
        }

        @Inject(method = "clipWithInteractionOverride", at = @At("RETURN"), cancellable = true)
        private void naturality$neighbor(net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to,
                BlockPos pos, VoxelShape shape, BlockState state,
                CallbackInfoReturnable<net.minecraft.world.phys.BlockHitResult> cir) {
            var hit = BannerPlacement.pick((BlockGetter)this, pos, from, to, cir.getReturnValue());
            if (hit != cir.getReturnValue()) cir.setReturnValue(hit);
        }
    }

    @Mixin(BannerBlock.class)
    public static abstract class StandingPlacement {
        @Inject(method = "getStateForPlacement", at = @At("HEAD"), cancellable = true)
        private void naturality$wallOnly(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
            cir.setReturnValue(null);
        }
    }

    @Mixin(WallBannerBlock.class)
    public static abstract class WallAttachment {
        @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
        private void naturality$clearance(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
            if (!BannerPlacement.hasRoom(context.getLevel(), context.getClickedPos())) cir.setReturnValue(null);
        }
        @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
        private void naturality$support(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
            cir.setReturnValue(BannerPlacement.canAttach(state, level, pos));
        }

        @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
        private void naturality$outline(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                CallbackInfoReturnable<VoxelShape> cir) {
            cir.setReturnValue(BannerPlacement.upperOutline(state, level, pos));
        }
    }

    @Mixin(net.minecraft.world.level.block.state.BlockBehaviour.class)
    public static abstract class Lifecycle {
        @Inject(method = "onPlace", at = @At("TAIL"))
        private void naturality$lower(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                BlockState oldState, boolean moved, CallbackInfo ci) {
            BannerPlacement.placeLower(level, pos, state);
        }

        @Inject(method = "tick", at = @At("HEAD"))
        private void naturality$repair(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos,
                net.minecraft.util.RandomSource random, CallbackInfo ci) {
            if (state.getBlock() instanceof WallBannerBlock) {
                if (BannerPlacement.hasRoom(level, pos)) BannerPlacement.placeLower(level, pos, state);
                else level.destroyBlock(pos, true);
            }
        }
    }

    @Mixin(net.minecraft.world.item.BlockItem.class)
    public static abstract class PlacementSound {
        @WrapOperation(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
        private void naturality$sound(net.minecraft.world.level.Level level, net.minecraft.world.entity.@org.jspecify.annotations.Nullable Entity except,
                BlockPos pos, net.minecraft.sounds.SoundEvent sound, net.minecraft.sounds.SoundSource source, float volume, float pitch,
                Operation<Void> original, @Local(name = "placedState") BlockState state) {
            if (state.getBlock() instanceof WallBannerBlock) {
                var center = BannerPlacement.soundPosition(state, level, pos);
                level.playSound(except, center.x, center.y, center.z, sound, source, volume, pitch);
            } else original.call(level, except, pos, sound, source, volume, pitch);
        }
    }
}
