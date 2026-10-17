package naturality.systems.swimming.mixins;

import naturality.systems.swimming.PlayerSwimming;
import naturality.systems.swimming.WaterSurface;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Underwater player locomotion and shared surface buoyancy; mob dimensions stay vanilla. */
public final class SwimmingMixins {
    private SwimmingMixins() { }

    @Mixin(Player.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PlayerSwimmingStateMixin implements PlayerSwimming.Access {
        @Unique private static final EntityDataAccessor<Float> naturality$SWIM_METER =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.FLOAT);
        @Unique private PlayerSwimming.@Nullable State naturality$swimState;
        @Unique private static final EntityDataAccessor<Boolean> naturality$COMPACT_TERRAIN =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);
        public boolean naturality$compactTerrain() { return ((Player)(Object)this).getEntityData().get(naturality$COMPACT_TERRAIN); }
        public void naturality$compactTerrain(boolean value) { ((Player)(Object)this).getEntityData().set(naturality$COMPACT_TERRAIN, value); }
        @Unique private static final EntityDataAccessor<Boolean> naturality$BRACED =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);
        public boolean naturality$braced() { return ((Player)(Object)this).getEntityData().get(naturality$BRACED); }
        public void naturality$braced(boolean value) { ((Player)(Object)this).getEntityData().set(naturality$BRACED, value); }
        public PlayerSwimming.State naturality$swimming() {
            var state = naturality$swimState;
            if (state == null) naturality$swimState = state = new PlayerSwimming.State();
            return state;
        }
        public float naturality$swimMeter() { return ((Player)(Object)this).getEntityData().get(naturality$SWIM_METER); }
        public void naturality$swimMeter(float value) { ((Player)(Object)this).getEntityData().set(naturality$SWIM_METER, value); }
        @Inject(method = "defineSynchedData", at = @At("TAIL"))
        private void naturality$define(SynchedEntityData.Builder builder, CallbackInfo ci) {
            builder.define(naturality$SWIM_METER, -1F);
            builder.define(naturality$BRACED, false);
            builder.define(naturality$COMPACT_TERRAIN, false);
        }
        @Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
        private void naturality$mode(CallbackInfo ci) {
            var player = (Player)(Object)this;
            if (naturality.systems.swimming.WaterSurface.riptiding(player)) { PlayerSwimming.releaseForRiptide(player); return; }
            PlayerSwimming.updateMode((Player)(Object)this);
            ci.cancel();
        }
        @Inject(method = "startAutoSpinAttack", at = @At("TAIL"))
        private void naturality$riptide(int ticks, float damage, net.minecraft.world.item.ItemStack item, CallbackInfo ci) {
            var player = (Player)(Object)this;
            PlayerSwimming.startRiptide(player, ticks);
        }
        @Inject(method = "tick", at = @At("TAIL"))
        private void naturality$tick(CallbackInfo ci) { PlayerSwimming.tick((Player)(Object)this); }
        @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
        private void naturality$pose(CallbackInfo ci) {
            var player = (Player)(Object)this;
            if (PlayerSwimming.fullSize(player)) {
                player.setPose(Pose.STANDING);
                ci.cancel();
            }
        }
        @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
        private void naturality$travel(Vec3 input, CallbackInfo ci) {
            var player = (Player)(Object)this;
            if (PlayerSwimming.waterMovement(player)) { PlayerSwimming.travel(player, input); ci.cancel(); }
        }
        @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
        private void naturality$save(ValueOutput output, CallbackInfo ci) {
            output.putBoolean("naturality:swimming_rotation", naturality$swimming().ownsRotation);
        }
        @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
        private void naturality$load(ValueInput input, CallbackInfo ci) {
            naturality$swimming().ownsRotation = input.getBooleanOr("naturality:swimming_rotation", false);
        }
    }

    @Mixin(Avatar.class)
    public static abstract class PlayerSwimmingDimensionsMixin {
        @Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
        private void naturality$dimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
            if (pose != Pose.STANDING && (Object)this instanceof Player player && PlayerSwimming.fullSize(player))
                cir.setReturnValue(player.getDefaultDimensions(Pose.STANDING));
        }
    }

    @Mixin(LivingEntity.class)
    public static abstract class PlayerSwimmingJumpMixin {
        @Inject(method = {"jumpInLiquid", "goDownInWater", "jumpFromGround"}, at = @At("HEAD"), cancellable = true)
        private void naturality$waterJump(CallbackInfo ci) {
            if ((Object)this instanceof Player player && PlayerSwimming.waterMovement(player)) ci.cancel();
        }
    }

    @Mixin(LivingEntity.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WaterSurfaceLivingMixin implements WaterSurface.Access {
        @Unique private WaterSurface.@Nullable State naturality$surface;
        public WaterSurface.State naturality$waterSurface() {
            // Rotation/shape queries can run from the Entity constructor before field initializers.
            var state = naturality$surface;
            if (state == null) naturality$surface = state = new WaterSurface.State();
            return state;
        }
        @Inject(method = "aiStep", at = @At("HEAD"))
        private void naturality$surfaceUpdate(CallbackInfo ci) { WaterSurface.update((LivingEntity)(Object)this); }
        @Inject(method = "travel", at = @At("HEAD"))
        private void naturality$buoyancy(Vec3 input, CallbackInfo ci) { WaterSurface.buoyancy((LivingEntity)(Object)this); }
        @Inject(method = "jumpInLiquid", at = @At("HEAD"), cancellable = true)
        private void naturality$noBobbing(CallbackInfo ci) {
            if (WaterSurface.controlsHeight((LivingEntity)(Object)this)) ci.cancel();
        }
        @Inject(method = "tick", at = @At("TAIL"))
        private void naturality$surfaceMotion(CallbackInfo ci) { WaterSurface.tick((LivingEntity)(Object)this); }
    }

    @Mixin(Entity.class)
    public static abstract class PlayerSwimmingSneakMixin {
        @Inject(method = "onInsideBubbleColumn", at = @At("HEAD"))
        private void naturality$bubbleContact(boolean down, CallbackInfo ci) {
            if ((Object)this instanceof LivingEntity entity) WaterSurface.bubbleContact(entity);
        }
        @Inject(method = "onAboveBubbleColumn", at = @At("HEAD"))
        private void naturality$bubbleLaunch(boolean down, net.minecraft.core.BlockPos pos, CallbackInfo ci) {
            if ((Object)this instanceof LivingEntity entity) WaterSurface.bubbleContact(entity);
        }
        @Inject(method = "restituteMovementAfterCollisions", at = @At("HEAD"), cancellable = true)
        private void naturality$slideVelocity(BlockState state, boolean xCollision, boolean zCollision,
                                             Vec3 movement, CallbackInfo ci) {
            if ((Object)this instanceof Player player && PlayerSwimming.retainSlideVelocity(player, movement))
                ci.cancel();
        }
        @Inject(method = "isVisuallySwimming", at = @At("HEAD"), cancellable = true)
        private void naturality$animation(CallbackInfoReturnable<Boolean> cir) {
            if ((Object)this instanceof Player player && PlayerSwimming.active(player)) cir.setReturnValue(true);
        }
        @Inject(method = "isShiftKeyDown", at = @At("HEAD"), cancellable = true)
        private void naturality$sneak(CallbackInfoReturnable<Boolean> cir) {
            if ((Object)this instanceof Player player && PlayerSwimming.waterMovement(player)) cir.setReturnValue(false);
        }
    }
}
