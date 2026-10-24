package naturality.effects.stunned.mixins;

import naturality.effects.stunned.Stunned;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Independent action, AI and synchronization hooks for the stunned effect. */
@NullMarked
public final class StunnedMixins {
    private StunnedMixins() {}

    @Mixin(LivingEntity.class)
    public static abstract class Living implements Stunned.State {
        @Unique private static final EntityDataAccessor<Boolean> naturality$STUNNED =
            SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.BOOLEAN);

        @Inject(method = "defineSynchedData", at = @At("TAIL"))
        private void naturality$define(SynchedEntityData.Builder builder, CallbackInfo ci) {
            builder.define(naturality$STUNNED, false);
        }

        @Override public boolean naturality$isStunned() {
            return ((LivingEntity)(Object)this).getEntityData().get(naturality$STUNNED);
        }

        @Override public void naturality$setStunned(boolean stunned) {
            ((LivingEntity)(Object)this).getEntityData().set(naturality$STUNNED, stunned);
        }

        @Inject(method = {"onEffectAdded", "onEffectUpdated", "onEffectsRemoved"}, at = @At("TAIL"))
        private void naturality$effectsChanged(CallbackInfo ci) {
            Stunned.synchronize((LivingEntity)(Object)this);
        }

        @Inject(method = "tick", at = @At("HEAD"))
        private void naturality$stopActions(CallbackInfo ci) {
            Stunned.synchronize((LivingEntity)(Object)this);
        }

        @Inject(method = {"startUsingItem", "jumpFromGround", "jumpInLiquid", "goDownInWater"},
            at = @At("HEAD"), cancellable = true)
        private void naturality$noAction(CallbackInfo ci) {
            if (Stunned.active((LivingEntity)(Object)this)) ci.cancel();
        }

        @Inject(method = "updateUsingItem", at = @At("HEAD"), cancellable = true)
        private void naturality$interruptUse(CallbackInfo ci) {
            var entity = (LivingEntity)(Object)this;
            if (Stunned.active(entity)) { entity.stopUsingItem(); ci.cancel(); }
        }

        @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
        private void naturality$noAttackTarget(CallbackInfoReturnable<Boolean> result) {
            if (Stunned.active((LivingEntity)(Object)this)) result.setReturnValue(false);
        }
    }

    @Mixin(Entity.class)
    public static abstract class Movement {
        @Inject(method = "moveRelative", at = @At("HEAD"), cancellable = true)
        private void naturality$noMovementInput(CallbackInfo ci) {
            // Cancel input acceleration, not move(), so knockback and gravity still work.
            if (Stunned.active((Entity)(Object)this)) ci.cancel();
        }
    }

    @Mixin(Mob.class)
    public static abstract class Mobs {
        @Shadow @Final private GoalSelector goalSelector;
        @Shadow @Final private GoalSelector targetSelector;

        @SuppressWarnings("null") // The Mob constructor initializes both non-null shadow selectors.
        private Mobs() {}

        @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
        private void naturality$pauseAi(CallbackInfo ci) {
            var mob = (Mob)(Object)this;
            if (Stunned.active(mob)) { Stunned.stopAi(mob, goalSelector, targetSelector); ci.cancel(); }
        }

        @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
        private void naturality$noRetarget(@Nullable LivingEntity target, CallbackInfo ci) {
            if (target != null && Stunned.active((Mob)(Object)this)) ci.cancel();
        }

        @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
        private void naturality$noMelee(CallbackInfoReturnable<Boolean> result) {
            if (Stunned.active((Mob)(Object)this)) result.setReturnValue(false);
        }
    }

    @Mixin(Player.class)
    public static abstract class Players {
        @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
        private void naturality$noAttack(CallbackInfo ci) {
            if (Stunned.active((Player)(Object)this)) ci.cancel();
        }

        @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
        private void naturality$noInteraction(CallbackInfoReturnable<InteractionResult> result) {
            if (Stunned.active((Player)(Object)this)) result.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Mixin(ServerPlayerGameMode.class)
    public static abstract class BlockActions {
        @Shadow @Final protected ServerPlayer player;
        @Shadow protected ServerLevel level;
        @Shadow private boolean isDestroyingBlock;
        @Shadow private boolean hasDelayedDestroy;
        @Shadow private BlockPos destroyPos;
        @Shadow private BlockPos delayedDestroyPos;
        @Shadow private int lastSentState;

        @SuppressWarnings("null") // The target constructor/field initializers populate these shadows.
        private BlockActions() {}

        @Inject(method = "tick", at = @At("HEAD"))
        private void naturality$cancelMining(CallbackInfo ci) {
            if (!Stunned.active(player)) return;
            if (isDestroyingBlock) level.destroyBlockProgress(player.getId(), destroyPos, -1);
            if (hasDelayedDestroy) level.destroyBlockProgress(player.getId(), delayedDestroyPos, -1);
            isDestroyingBlock = false;
            hasDelayedDestroy = false;
            lastSentState = -1;
        }

        @Inject(method = "handleBlockBreakAction", at = @At("HEAD"), cancellable = true)
        private void naturality$noMining(BlockPos pos, ServerboundPlayerActionPacket.Action action,
                Direction direction, int maxY, int sequence, CallbackInfo ci) {
            if (Stunned.active(player)) {
                player.connection.send(new ClientboundBlockUpdatePacket(pos, level.getBlockState(pos)));
                ci.cancel();
            }
        }

        @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
        private void naturality$noBlockBreak(CallbackInfoReturnable<Boolean> result) {
            if (Stunned.active(player)) result.setReturnValue(false);
        }

        @Inject(method = {"useItem", "useItemOn"}, at = @At("HEAD"), cancellable = true)
        private void naturality$noUse(CallbackInfoReturnable<InteractionResult> result) {
            if (Stunned.active(player)) result.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Mixin(ServerPlayer.class)
    public static abstract class PlayerMovement {
        @ModifyVariable(method = "setLastClientInput", at = @At("HEAD"), argsOnly = true)
        private Input naturality$noClientMovementInput(Input input) {
            return Stunned.active((ServerPlayer)(Object)this) ? Input.EMPTY : input;
        }
    }

    @Mixin(ServerGamePacketListenerImpl.class)
    public static abstract class PlayerActions {
        @Shadow public ServerPlayer player;

        @SuppressWarnings("null") // The packet listener constructor initializes its player shadow.
        private PlayerActions() {}

        @Inject(method = {"handleInteract", "handlePlayerCommand", "handlePlayerAbilities",
            "handleContainerClick", "handleContainerButtonClick", "handleSetCreativeModeSlot", "handleSetCarriedItem"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                shift = At.Shift.AFTER), cancellable = true)
        private void naturality$rejectAction(CallbackInfo ci) {
            if (Stunned.active(player)) {
                player.containerMenu.sendAllDataToRemote();
                player.connection.send(new ClientboundSetHeldSlotPacket(player.getInventory().getSelectedSlot()));
                ci.cancel();
            }
        }

        @Inject(method = "handlePlayerAction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER), cancellable = true)
        private void naturality$noDropOrSwap(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
            if (!Stunned.active(player)) return;
            switch (packet.getAction()) {
                case DROP_ITEM, DROP_ALL_ITEMS, SWAP_ITEM_WITH_OFFHAND -> {
                    player.inventoryMenu.sendAllDataToRemote();
                    ci.cancel();
                }
                default -> { } // Block actions keep vanilla acknowledgement and use the game-mode guards.
            }
        }
    }
}
