package naturality.client.portals.end.mixins;

import naturality.client.portals.end.EndPortalBorderState.EndEyeGlow;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.LevelEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import naturality.client.portals.end.EndPortalBorderState.EndPortalBorderAccess;
import naturality.client.portals.end.EndPortalBorderState;
import net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer;
import net.minecraft.client.renderer.blockentity.state.EndPortalRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Unique;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class EndMixins {
    private EndMixins() {}

    @Mixin(LevelEventHandler.class)
    @org.jspecify.annotations.NullMarked
    public static class EndEyeInsertionMixin {
        @SuppressWarnings("null") @Shadow @Final private ClientLevel level;

        @Inject(method = "levelEvent", at = @At("HEAD"), cancellable = true)
        private void naturality$eyeGlow(int eventType, BlockPos pos, int data, CallbackInfo ci) {
            if (!naturality.config.live.NaturalityConfig.get().portalChanges.eyeGlow || eventType != LevelEvent.END_PORTAL_FRAME_FILL) return;
            EndEyeGlow.start(level, pos);
            level.playLocalSound(pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1, 1, false);
            // Replace the insertion puff as well; retain vanilla positional audio.
            ci.cancel();
        }
    }

    @Mixin(AbstractEndPortalRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static class EndPortalBorderExtractMixin {
        @Inject(method = "extractRenderState", at = @At("TAIL"))
        private void naturality$extract(TheEndPortalBlockEntity entity, EndPortalRenderState state,
                float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay breaking, CallbackInfo ci) {
            ((EndPortalBorderAccess) state).naturality$setBorder(EndPortalBorderState.extract(entity, camera));
        }
    }

    @Mixin(EndPortalRenderState.class)
    @org.jspecify.annotations.NullMarked
    public static class EndPortalBorderStateMixin implements EndPortalBorderAccess {
        @Unique private @org.jspecify.annotations.Nullable EndPortalBorderState naturality$border;
        public @org.jspecify.annotations.Nullable EndPortalBorderState naturality$getBorder() { return naturality$border; }
        public void naturality$setBorder(@org.jspecify.annotations.Nullable EndPortalBorderState state) { naturality$border = state; }
    }

    @Mixin(TheEndPortalRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static class EndPortalBorderSubmitMixin {
        @Inject(method = "submit", at = @At("TAIL"))
        private void naturality$submit(EndPortalRenderState state, PoseStack pose, SubmitNodeCollector collector,
                CameraRenderState camera, CallbackInfo ci) {
            var border = ((EndPortalBorderAccess) state).naturality$getBorder();
            if (border != null) border.submit(collector);
        }
    }

    @Mixin(EndPortalBlock.class)
    @org.jspecify.annotations.NullMarked
    public static class EndPortalSmokeMixin {
        @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
        private void naturality$noSmoke(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
            if (naturality.config.live.NaturalityConfig.get().portalChanges.suppressEndSmoke) ci.cancel();
        }
    }
}
