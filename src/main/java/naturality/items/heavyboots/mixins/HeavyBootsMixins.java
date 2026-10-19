package naturality.items.heavyboots.mixins;

import naturality.items.heavyboots.HeavyBoots;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.advancements.predicates.entity.EntityFlagsPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Application hooks for the footwear's vanilla walking mode. */
public final class HeavyBootsMixins {
    private HeavyBootsMixins() { }

    @Mixin(Entity.class)
    public static abstract class HeavyBootsFallMixin {
        @WrapOperation(method = "updateFluidInteraction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;resetFallDistance()V"))
        private void naturality$keepWaterFall(Entity entity, Operation<Void> original) {
            if (!(entity instanceof LivingEntity living) || !HeavyBoots.walkingEligible(living)) original.call(entity);
        }

        @ModifyExpressionValue(method = "checkFallDamage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;isInWater()Z"))
        private boolean naturality$countDescent(boolean inWater) {
            return inWater && !((Object)this instanceof LivingEntity living && HeavyBoots.active(living));
        }

        // Fast falls also raycast ahead for water. Keep solid fall-resetting blocks intact.
        @ModifyArg(method = "move", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/ClipContext;<init>(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;Lnet/minecraft/world/entity/Entity;)V"), index = 3)
        private ClipContext.Fluid naturality$fallThroughWater(ClipContext.Fluid fluid) {
            return (Object)this instanceof LivingEntity living && HeavyBoots.walkingEligible(living)
                ? ClipContext.Fluid.NONE : fluid;
        }
    }

    @Mixin(Enchantment.class)
    public static abstract class HeavyBootsLungeMixin {
        @WrapMethod(method = "doPostPiercingAttack")
        private void naturality$lungeOnSeabed(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item,
                                              Entity user, Operation<Void> original) {
            var lunge = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LUNGE);
            if ((Object)this == lunge.value() && user instanceof LivingEntity living && HeavyBoots.active(living))
                HeavyBoots.withLungeUser(user, () -> original.call(level, enchantmentLevel, item, user));
            else original.call(level, enchantmentLevel, item, user);
        }
    }

    @Mixin(EntityFlagsPredicate.class)
    public static abstract class HeavyBootsLungeConditionMixin {
        @WrapOperation(method = "matches(Lnet/minecraft/world/entity/Entity;)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;isInWater()Z"))
        private boolean naturality$lungeWaterCondition(Entity entity, Operation<Boolean> original) {
            return !HeavyBoots.lungeTreatsAsDry(entity) && original.call(entity);
        }
    }

    @Mixin(LivingEntity.class)
    public static abstract class HeavyBootsMovementMixin {
        @Shadow public abstract void jumpFromGround();

        @Inject(method = "shouldTravelInFluid", at = @At("HEAD"), cancellable = true)
        private void naturality$walk(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
            if (HeavyBoots.active((LivingEntity)(Object)this)) cir.setReturnValue(false);
        }

        @ModifyVariable(method = "travelInAir", at = @At("HEAD"), argsOnly = true)
        private Vec3 naturality$slowWalk(Vec3 input) {
            return HeavyBoots.active((LivingEntity)(Object)this) ? HeavyBoots.walkingInput(input) : input;
        }

        @Inject(method = "getEffectiveGravity", at = @At("RETURN"), cancellable = true)
        private void naturality$gravity(CallbackInfoReturnable<Double> cir) {
            if (HeavyBoots.active((LivingEntity)(Object)this))
                cir.setReturnValue(cir.getReturnValueD() * HeavyBoots.GRAVITY_SCALE);
        }

        @Inject(method = "jumpInLiquid", at = @At("HEAD"), cancellable = true)
        private void naturality$groundJump(TagKey<Fluid> fluid, CallbackInfo ci) {
            var entity = (LivingEntity)(Object)this;
            if (fluid.equals(FluidTags.WATER) && HeavyBoots.active(entity)) {
                if (entity.onGround()) jumpFromGround();
                ci.cancel();
            }
        }

        @Inject(method = "goDownInWater", at = @At("HEAD"), cancellable = true)
        private void naturality$noWaterDescent(CallbackInfo ci) {
            if (HeavyBoots.active((LivingEntity)(Object)this)) ci.cancel();
        }
    }
}
