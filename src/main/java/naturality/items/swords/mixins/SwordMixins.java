package naturality.items.swords.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import naturality.items.swords.SwordCounter;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sword integration; counter behavior and state live in SwordCounter. */
@NullMarked
public final class SwordMixins {
    private SwordMixins() {}

    @Mixin(Projectile.class)
    public static abstract class CounterProjectile implements SwordCounter.ProjectileState {
        @Shadow private boolean leftOwner;
        @Shadow private boolean leftOwnerChecked;
        @Unique private final SwordCounter.ProjectileGrace naturality$parryGrace = new SwordCounter.ProjectileGrace();

        @Override public void naturality$resetParryCollision(Player player, boolean changedOwner) {
            // Retained owners must remain hittable on the return trip; changing ownership uses vanilla grace.
            leftOwner = !changedOwner;
            leftOwnerChecked = false;
            naturality$parryGrace.begin(changedOwner ? null : player);
        }

        @Inject(method = "tick", at = @At("HEAD"))
        private void naturality$expireParryGrace(CallbackInfo ci) {
            naturality$parryGrace.tick((Projectile)(Object)this);
        }

        @ModifyReturnValue(method = "canHitEntity", at = @At("RETURN"))
        private boolean naturality$protectDefender(boolean original, Entity target) {
            return original && !naturality$parryGrace.blocks(target);
        }

        @Inject(method = "hitTargetOrDeflectSelf", at = @At("HEAD"), cancellable = true)
        private void naturality$returnProjectile(HitResult hit, CallbackInfoReturnable<ProjectileDeflection> result) {
            if (hit instanceof EntityHitResult entityHit
                    && SwordCounter.returnProjectile((Projectile)(Object)this, entityHit)) {
                // A non-NONE result also stops piercing arrows from processing the rest of this impact.
                result.setReturnValue(ProjectileDeflection.AIM_DEFLECT);
            }
        }
    }

    @Mixin(ShulkerBullet.class)
    public static abstract class CounterHomingProjectile implements SwordCounter.HomingProjectile {
        @Shadow private @Nullable EntityReference<Entity> finalTarget;
        @Shadow private @Nullable Direction currentMoveDirection;
        @Shadow private int flightSteps;
        @Shadow private double targetDeltaX;
        @Shadow private double targetDeltaY;
        @Shadow private double targetDeltaZ;

        @Override public void naturality$retarget(LivingEntity target, Vec3 movement) {
            finalTarget = EntityReference.of(target);
            currentMoveDirection = null;
            // The current tick still holds the old target in a local; defer route selection until later.
            flightSteps = 10;
            targetDeltaX = movement.x;
            targetDeltaY = movement.y;
            targetDeltaZ = movement.z;
        }
    }

    @Mixin(Item.class)
    public static abstract class CounterUse {
        @Inject(method = "use", at = @At("HEAD"), cancellable = true)
        private void naturality$counter(Level level, Player player, InteractionHand hand,
                CallbackInfoReturnable<InteractionResult> result) {
            InteractionResult counter = SwordCounter.use(player, hand);
            if (counter != InteractionResult.PASS) result.setReturnValue(counter);
        }

        @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
        private void naturality$blockingPose(ItemStack stack, CallbackInfoReturnable<ItemUseAnimation> result) {
            if (SwordCounter.isSword(stack)) result.setReturnValue(ItemUseAnimation.BLOCK);
        }

        @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
        private void naturality$counterDuration(ItemStack stack, LivingEntity user, CallbackInfoReturnable<Integer> result) {
            if (user instanceof Player && SwordCounter.isSword(stack)) result.setReturnValue(SwordCounter.windowTicks(stack));
        }
    }

    @Mixin(ItemCooldowns.class)
    public static abstract class CounterCooldown {
        @ModifyReturnValue(method = "getCooldownGroup", at = @At("RETURN"))
        private Identifier naturality$sharedSwordCooldown(Identifier original, ItemStack stack) {
            return SwordCounter.cooldownGroup(stack, original);
        }
    }

    @Mixin(LivingEntity.class)
    public static abstract class CounterWindow {
        @Inject(method = "updateUsingItem", at = @At("RETURN"))
        private void naturality$endBlockingPose(ItemStack stack, CallbackInfo ci) {
            var entity = (LivingEntity)(Object)this;
            // Vanilla finishes timed use on the server only; end the predicted pose on the same client tick.
            if (entity instanceof Player && SwordCounter.isSword(stack) && entity.getUseItemRemainingTicks() <= 0) {
                entity.stopUsingItem();
            }
        }

        @Inject(method = "releaseUsingItem", at = @At("HEAD"), cancellable = true)
        private void naturality$keepClickWindow(CallbackInfo ci) {
            // A click opens the entire short window; releasing the button does not shorten it.
            if (SwordCounter.isCounterUse((LivingEntity)(Object)this)) ci.cancel();
        }
    }
}
