package naturality.items.durability.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.items.durability.ItemDurability;
import naturality.systems.enchanting.curses.Breaking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Independent hooks for retained items, free renaming and material-only anvil repairs. */
public final class DurabilityMixins {
    private DurabilityMixins() {}

    @Mixin(ItemStack.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class DurabilityStackMixin {
        @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "applyDamage")
        private void naturality$breakOnce(int newDamage, net.minecraft.server.level.@Nullable ServerPlayer player,
                java.util.function.Consumer<ItemStack> onBreak, Operation<Void> original) {
            var stack = (ItemStack)(Object)this;
            if (stack.isBroken() && !Breaking.destroys(stack) && newDamage >= stack.getDamageValue()) return;
            original.call(newDamage, player, onBreak);
        }

        @WrapOperation(method = "applyDamage", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
        private void naturality$retainExhausted(ItemStack stack, int amount, Operation<Void> original) {
            // Breaking opts back into vanilla destruction; other items retain the break event.
            if (Breaking.destroys(stack)) original.call(stack, amount);
        }

        @Inject(method = "getDestroySpeed", at = @At("HEAD"), cancellable = true)
        private void naturality$noMiningSpeed(CallbackInfoReturnable<Float> cir) {
            if (((ItemStack)(Object)this).isBroken()) cir.setReturnValue(1.0F);
        }

        @Inject(method = "isCorrectToolForDrops", at = @At("HEAD"), cancellable = true)
        private void naturality$noHarvestStrength(CallbackInfoReturnable<Boolean> cir) {
            if (((ItemStack)(Object)this).isBroken()) cir.setReturnValue(false);
        }

        @Inject(method = {"use", "useOn", "interactLivingEntity"}, at = @At("HEAD"), cancellable = true)
        private void naturality$noUse(CallbackInfoReturnable<InteractionResult> cir) {
            if (((ItemStack)(Object)this).isBroken()) cir.setReturnValue(InteractionResult.PASS);
        }

        // Keep slot modifiers available for vanilla's removal of previously equipped effects.
        @Inject(method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlotGroup;Lorg/apache/commons/lang3/function/TriConsumer;)V",
            at = @At("HEAD"), cancellable = true)
        private void naturality$noTooltipStats(CallbackInfo ci) {
            if (((ItemStack)(Object)this).isBroken()) ci.cancel();
        }
    }

    @Mixin({LivingEntity.class, Player.class})
    @org.jspecify.annotations.NullMarked
    public static abstract class DurabilityWeaponMixin {
        @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "getWeaponItem", at = @At("RETURN"))
        private ItemStack naturality$noWeaponBonuses(ItemStack weapon) {
            return weapon.isBroken() ? ItemStack.EMPTY : weapon;
        }
    }

    @Mixin(LivingEntity.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class DurabilityBlockingMixin {
        @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "getItemBlockingWith", at = @At("RETURN"))
        private @Nullable ItemStack naturality$noBrokenShield(@Nullable ItemStack shield) {
            return shield != null && shield.isBroken() ? null : shield;
        }

        @Inject(method = "updateUsingItem", at = @At("HEAD"), cancellable = true)
        private void naturality$stopExhaustedUse(ItemStack stack, CallbackInfo ci) {
            if (stack.isBroken()) {
                ((LivingEntity)(Object)this).stopUsingItem();
                ci.cancel();
            }
        }
    }

    @Mixin(EnchantmentHelper.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class DurabilityEquipmentEnchantmentsMixin {
        @WrapOperation(method = "runIterationOnEquipment", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentInSlotVisitor;)V"))
        private static void naturality$noEquipmentBonuses(ItemStack stack, EquipmentSlot slot, LivingEntity owner,
                EnchantmentHelper.EnchantmentInSlotVisitor visitor, Operation<Void> original) {
            if (!stack.isBroken()) original.call(stack, slot, owner, visitor);
        }
    }

    @Mixin(AnvilMenu.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class DurabilityAnvilMixin extends ItemCombinerMenu {
        @Shadow @Final private DataSlot cost;
        @Shadow private int repairItemCountCost;
        @Shadow private boolean onlyRenaming;
        @Shadow private @Nullable String itemName;

        @SuppressWarnings("null") // The target constructor initializes the non-null shadow cost.
        protected DurabilityAnvilMixin(@Nullable MenuType<?> type, int id, Inventory inventory,
                ContainerLevelAccess access, ItemCombinerMenuSlotDefinition slots) {
            super(type, id, inventory, access, slots);
        }

        @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
        private void naturality$materialRepair(CallbackInfo ci) {
            var repair = ItemDurability.repair(inputSlots.getItem(0), inputSlots.getItem(1), itemName);
            if (repair == null) return;
            repairItemCountCost = repair.materials();
            onlyRenaming = repair.materials() == 0;
            cost.set(0);
            resultSlots.setItem(0, repair.result());
            broadcastChanges();
            ci.cancel();
        }

        @ModifyVariable(method = "createResult", at = @At("STORE"), name = "finalPrice")
        private int naturality$noNamingSurcharge(int original) {
            // Combining items keeps its normal cost; changing the name adds no extra level.
            return Math.max(0, original - (ItemDurability.hasNameChange(inputSlots.getItem(0), itemName) ? 1 : 0));
        }

        @Inject(method = "createResult", at = @At("TAIL"))
        private void naturality$renameWithUnusedAddition(CallbackInfo ci) {
            // Vanilla can produce a rename-only result with a second item that contributes no repair.
            if (!onlyRenaming || resultSlots.getItem(0).isEmpty()) return;
            cost.set(0);
            var priorWork = inputSlots.getItem(0).get(DataComponents.REPAIR_COST);
            if (priorWork == null) resultSlots.getItem(0).remove(DataComponents.REPAIR_COST);
            else resultSlots.getItem(0).set(DataComponents.REPAIR_COST, priorWork);
            broadcastChanges();
        }

        @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
        private void naturality$freePickup(Player player, boolean hasItem, CallbackInfoReturnable<Boolean> cir) {
            if (cost.get() == 0 && hasItem && !resultSlots.getItem(0).isEmpty()
                    && (onlyRenaming || ItemDurability.repair(inputSlots.getItem(0), inputSlots.getItem(1), itemName) != null)) {
                cir.setReturnValue(true);
            }
        }
    }
}
