package naturality.systems.enchanting.curses.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.function.Consumer;
import naturality.systems.enchanting.curses.Bewitchment;
import naturality.systems.enchanting.curses.Binding;
import naturality.systems.enchanting.curses.Lethargy;
import naturality.systems.enchanting.curses.Perfection;
import naturality.systems.enchanting.curses.Poverty;
import naturality.systems.enchanting.curses.Reflection;
import naturality.systems.enchanting.curses.Vulnerability;
import naturality.systems.enchanting.curses.Weakness;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Wiring only; individual curses own their behavior in the parent package. */
@org.jspecify.annotations.NullMarked
public final class CurseMixins {
    private CurseMixins() {}

    @Mixin(LivingEntity.class)
    public static abstract class Living {
        @ModifyReturnValue(method = "getArmorValue", at = @At("RETURN"))
        private int naturality$vulnerability(int armor) {
            return Vulnerability.armor((LivingEntity)(Object)this, armor);
        }

        @WrapMethod(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z")
        private boolean naturality$bewitchment(MobEffectInstance effect, @Nullable Entity source, Operation<Boolean> original) {
            return original.call(Bewitchment.extend((LivingEntity)(Object)this, effect), source);
        }

        @WrapMethod(method = "hurtServer")
        private boolean naturality$reflection(ServerLevel world, DamageSource source, float damage, Operation<Boolean> original) {
            Reflection.begin();
            try {
                var hit = Reflection.before((LivingEntity)(Object)this, source);
                boolean accepted = original.call(world, source, damage);
                Reflection.after(hit, accepted);
                return accepted;
            } finally { Reflection.end(); }
        }
    }

    @Mixin(CombatRules.class)
    public static abstract class ArmorDamage {
        @Inject(method = "getDamageAfterAbsorb", at = @At("HEAD"), cancellable = true)
        private static void naturality$negativeArmor(LivingEntity victim, float damage, DamageSource source,
                float armor, float toughness, CallbackInfoReturnable<Float> result) {
            if (armor < 0) result.setReturnValue(Vulnerability.negativeArmorDamage(damage, armor));
        }
    }

    @Mixin(EnchantmentHelper.class)
    public static abstract class Equipment {
        @WrapMethod(method = "modifyDamage")
        private static float naturality$weakProjectile(ServerLevel world, ItemStack weapon, Entity target,
                DamageSource source, float damage, Operation<Float> original) {
            return original.call(world, weapon, target, source,
                source.getDirectEntity() instanceof AbstractArrow ? Weakness.damage(weapon, damage) : damage);
        }

        @ModifyReturnValue(method = "has", at = @At("RETURN"))
        private static boolean naturality$brokenBinding(boolean value, ItemStack stack, DataComponentType<?> effect) {
            return Binding.prevents(value, stack, effect);
        }
    }

    @Mixin(Player.class)
    public static abstract class Players {
        @Shadow protected abstract boolean canCriticalAttack(Entity target);

        @ModifyVariable(method = "attack", at = @At(value = "STORE", ordinal = 0), ordinal = 0)
        private float naturality$weakAttack(float damage) {
            return Weakness.damage(((Player)(Object)this).getWeaponItem(), damage);
        }

        @ModifyReturnValue(method = "getDestroySpeed", at = @At("RETURN"))
        private float naturality$lethargy(float speed) {
            return Lethargy.speed(((Player)(Object)this).getMainHandItem(), speed);
        }

        @WrapMethod(method = "attack")
        private void naturality$attack(Entity target, Operation<Void> original) {
            var player = (Player)(Object)this;
            if (Perfection.blocks(player.getWeaponItem(), player.getAttackStrengthScale(0.5F) > 0.9F && canCriticalAttack(target))) return;
            Reflection.begin();
            try { original.call(target); } finally { Reflection.end(); }
        }
    }

    @Mixin(Mob.class)
    public static abstract class Mobs {
        @ModifyVariable(method = "doHurtTarget", at = @At(value = "STORE", ordinal = 0), ordinal = 0)
        private float naturality$weakAttack(float damage) {
            return Weakness.damage(((Mob)(Object)this).getWeaponItem(), damage);
        }

        @WrapMethod(method = "doHurtTarget")
        private boolean naturality$attack(ServerLevel world, Entity target, Operation<Boolean> original) {
            if (Perfection.blocks(((Mob)(Object)this).getWeaponItem(), false)) return false;
            Reflection.begin();
            try { return original.call(world, target); } finally { Reflection.end(); }
        }
    }

    @Mixin({LivingEntity.class, Player.class})
    public static abstract class Piercing {
        @ModifyVariable(method = "stabAttack", at = @At("HEAD"), argsOnly = true, ordinal = 0)
        private float naturality$weakStab(float damage, @Local(argsOnly = true) EquipmentSlot slot) {
            return Weakness.damage(((LivingEntity)(Object)this).getItemBySlot(slot), damage);
        }
    }

    @Mixin({AbstractArrow.class, ThrownTrident.class})
    public static abstract class Projectiles {
        @WrapMethod(method = "onHitEntity")
        private void naturality$attack(EntityHitResult hit, Operation<Void> original) {
            var arrow = (AbstractArrow)(Object)this;
            if (Perfection.blocks(arrow.getWeaponItem(), arrow.isCritArrow())) return;
            Reflection.begin();
            try { original.call(hit); } finally { Reflection.end(); }
        }
    }

    @Mixin(LootTable.class)
    public static abstract class Loot {
        @WrapMethod(method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V")
        private void naturality$poverty(LootContext context, Consumer<ItemStack> output, Operation<Void> original) {
            Poverty.generate(context, output, consumer -> original.call(context, consumer));
        }
    }
}
