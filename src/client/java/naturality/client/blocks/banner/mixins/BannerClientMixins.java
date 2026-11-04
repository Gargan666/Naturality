package naturality.client.blocks.banner.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import naturality.client.blocks.banner.BannerVisuals;
import net.minecraft.client.model.object.banner.BannerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.state.BannerRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import naturality.blocks.banner.BannerPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public final class BannerClientMixins {
    private BannerClientMixins() {}

    @Mixin(net.minecraft.client.renderer.LevelEventHandler.class)
    public static abstract class BreakSound {
        @WrapOperation(method = "levelEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
        private void naturality$sound(ClientLevel level, BlockPos pos, SoundEvent sound, SoundSource source,
                float volume, float pitch, boolean delay, Operation<Void> original,
                @Local(argsOnly = true, ordinal = 0) int event, @Local(argsOnly = true, ordinal = 1) int data) {
            BlockState state = event == 2001 ? Block.stateById(data) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            if (state.getBlock() instanceof WallBannerBlock || state.getBlock() instanceof BannerPlacement.Segment) {
                Vec3 center = BannerPlacement.soundPosition(state, level, pos);
                level.playLocalSound(center.x, center.y, center.z, sound, source, volume, pitch, delay);
            } else original.call(level, pos, sound, source, volume, pitch, delay);
        }
    }

    @Mixin(ClientLevel.class)
    public static abstract class HitSound {
        @Inject(method = "playBreakingSound", at = @At("HEAD"), cancellable = true)
        private void naturality$sound(BlockPos pos, BlockState state, CallbackInfo ci) {
            if (state.getBlock() instanceof WallBannerBlock || state.getBlock() instanceof BannerPlacement.Segment) {
                ClientLevel level = (ClientLevel)(Object)this;
                Vec3 center = BannerPlacement.soundPosition(state, level, pos);
                var sound = state.getSoundType();
                level.playLocalSound(center.x, center.y, center.z, sound.getHitSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0F) / 8.0F, sound.getPitch() * 0.5F, false);
                ci.cancel();
            }
        }
    }

    @Mixin(BannerRenderer.class)
    public static abstract class Rendering {
        @Inject(method = "extractRenderState(Lnet/minecraft/world/level/block/entity/BannerBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/BannerRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V", at = @At("TAIL"))
        private void naturality$transform(BannerBlockEntity entity, BannerRenderState state, float partialTicks,
                Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breaking, CallbackInfo ci) {
            BannerVisuals.transform(entity, state);
        }

        @ModifyVariable(method = "submitBanner", at = @At("HEAD"), argsOnly = true)
        private static BannerModel naturality$stand(BannerModel model) {
            return BannerVisuals.stand(model);
        }
    }

    @Mixin(ItemStackRenderState.class)
    public static abstract class ItemContext {
        @Shadow ItemDisplayContext displayContext;

        @WrapMethod(method = "submit")
        private void naturality$context(PoseStack poses, SubmitNodeCollector collector, int light, int overlay,
                int outline, Operation<Void> original) {
            ItemDisplayContext previous = BannerVisuals.context(displayContext);
            try { original.call(poses, collector, light, overlay, outline); }
            finally { BannerVisuals.context(previous); }
        }
    }
}
