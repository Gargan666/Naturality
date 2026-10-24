package naturality.systems.enchanting.framework.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import naturality.systems.enchanting.framework.Enchanting;
import naturality.systems.enchanting.curses.Fragility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Always-active integration for the paired enchanting system. */
public final class EnchantingMixins {
    private EnchantingMixins() {}

    @Mixin(net.minecraft.world.entity.npc.villager.AbstractVillager.class)
    public static abstract class VillagerBooks {
        @Inject(method = "getOffers", at = @At("RETURN"))
        private void naturality$curseBooks(CallbackInfoReturnable<net.minecraft.world.item.trading.MerchantOffers> result) {
            var villager = (net.minecraft.world.entity.npc.villager.AbstractVillager)(Object)this;
            if (villager.level().isClientSide()) return;
            for (var offer : result.getReturnValue()) {
                Enchanting.curseTradeBook(offer.getResult(), villager.registryAccess(), villager.getRandom());
            }
        }
    }

    @Mixin(CreativeModeTabs.class)
    public static abstract class CreativeBooks {
        @Inject(method = "generateEnchantmentBookTypesOnlyMaxLevel", at = @At("HEAD"), cancellable = true)
        private static void naturality$creativeBooks(CreativeModeTab.Output output, HolderLookup<Enchantment> registry,
                CreativeModeTab.TabVisibility visibility, CallbackInfo ci) {
            Enchanting.creativeBooks(output, registry, visibility, false);
            ci.cancel();
        }
        @Inject(method = "generateEnchantmentBookTypesAllLevels", at = @At("HEAD"), cancellable = true)
        private static void naturality$searchBooks(CreativeModeTab.Output output, HolderLookup<Enchantment> registry,
                CreativeModeTab.TabVisibility visibility, CallbackInfo ci) {
            Enchanting.creativeBooks(output, registry, visibility, true);
            ci.cancel();
        }
    }

    @Mixin(EnchantingTableBlockEntity.class)
    public static abstract class TableStorage implements Enchanting.TableAccess {
        @Unique private final Enchanting.Table naturality$table = new Enchanting.Table((BlockEntity)(Object)this);
        @Override public Enchanting.Table naturality$enchanting() { return naturality$table; }
        @Inject(method = "saveAdditional", at = @At("TAIL"))
        private void naturality$save(ValueOutput output, CallbackInfo ci) { naturality$table.save(output); }
        @Inject(method = "loadAdditional", at = @At("TAIL"))
        private void naturality$load(ValueInput input, CallbackInfo ci) { naturality$table.load(input); }
    }

    @Mixin(EnchantingTableBlock.class)
    public static abstract class TableMenu {
        @Inject(method = "getMenuProvider", at = @At("HEAD"), cancellable = true)
        private void naturality$menu(BlockState state, Level level, BlockPos pos, CallbackInfoReturnable<MenuProvider> cir) {
            if (level.getBlockEntity(pos) instanceof EnchantingTableBlockEntity entity
                    && entity instanceof Enchanting.TableAccess access) {
                cir.setReturnValue(new SimpleMenuProvider((id, inventory, _) ->
                    new Enchanting.Menu(id, inventory, access.naturality$enchanting()), entity.getDisplayName()));
            }
        }
    }

    @Mixin(BlockEntity.class)
    public static abstract class TableDrops {
        @Inject(method = "getUpdateTag", at = @At("HEAD"), cancellable = true)
        private void naturality$tableContents(HolderLookup.Provider registries, CallbackInfoReturnable<net.minecraft.nbt.CompoundTag> cir) {
            if ((Object)this instanceof EnchantingTableBlockEntity table) cir.setReturnValue(table.saveWithoutMetadata(registries));
        }
        @Inject(method = "getUpdatePacket", at = @At("HEAD"), cancellable = true)
        private void naturality$tablePacket(CallbackInfoReturnable<net.minecraft.network.protocol.@Nullable Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>> cir) {
            if ((Object)this instanceof EnchantingTableBlockEntity table)
                cir.setReturnValue(net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(table));
        }
        @Inject(method = "preRemoveSideEffects", at = @At("HEAD"))
        private void naturality$dropInventory(BlockPos pos, BlockState state, CallbackInfo ci) {
            var entity = (BlockEntity)(Object)this;
            var level = entity.getLevel();
            if (level != null && !level.isClientSide() && this instanceof Enchanting.TableAccess access) {
                Containers.dropContents(level, pos, access.naturality$enchanting());
                access.naturality$enchanting().clearContent();
            }
        }
    }

    @Mixin(GrindstoneMenu.class)
    public static abstract class GrindstonePairs {
        @Inject(method = "removeNonCursesFrom", at = @At("HEAD"), cancellable = true)
        private void naturality$removePairs(ItemStack item, CallbackInfoReturnable<ItemStack> cir) {
            cir.setReturnValue(Enchanting.grind(item));
        }
    }

    @Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4")
    public static abstract class GrindstoneExperience {
        @Inject(method = "getExperienceAmount", at = @At("HEAD"), cancellable = true)
        private void naturality$noExperience(Level level, CallbackInfoReturnable<Integer> cir) { cir.setReturnValue(0); }
    }

    @Mixin(AnvilMenu.class)
    public static abstract class AnvilPairs extends ItemCombinerMenu {
        protected AnvilPairs(@Nullable MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access, ItemCombinerMenuSlotDefinition slots) {
            super(type, id, inventory, access, slots);
        }
        @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
        private void naturality$tableOnly(CallbackInfo ci) {
            var second = inputSlots.getItem(1);
            if (!second.isEmpty() && (second.has(DataComponents.STORED_ENCHANTMENTS) || second.isEnchanted())) {
                resultSlots.setItem(0, ItemStack.EMPTY);
                broadcastChanges();
                ci.cancel();
            }
        }
    }

    @Mixin(ItemEnchantments.class)
    public static abstract class HarvestingQueries {
        @ModifyReturnValue(method = "getLevel", at = @At("RETURN"))
        private int naturality$harvesting(int original, Holder<Enchantment> enchantment) {
            String id = Enchanting.name(enchantment);
            if (!id.equals("minecraft:fortune") && !id.equals("minecraft:looting")) return original;
            for (var entry : ((ItemEnchantments)(Object)this).entrySet()) {
                if (Enchanting.name(entry.getKey()).equals("naturality:harvesting")) return Math.max(original, entry.getIntValue());
            }
            return original;
        }
    }

    @Mixin(ItemStack.class)
    public static abstract class FragilityWear {
        @ModifyReturnValue(method = "processDurabilityChange", at = @At("RETURN"))
        private int naturality$wear(int wear, int amount, ServerLevel level, @Nullable ServerPlayer player) {
            return Fragility.wear((ItemStack)(Object)this, wear, level.getRandom());
        }
    }
}
