package naturality.client.systems.enchanting.mixins;

import java.util.function.Consumer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.Naturality;
import naturality.client.systems.enchanting.EnchantingClient;
import naturality.client.systems.enchanting.BookIconRendering;
import naturality.client.systems.enchanting.EnchantmentIcons;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import naturality.systems.enchanting.framework.Enchanting;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.resources.Identifier;
import java.util.Optional;
import java.util.List;
import net.minecraft.client.gui.Font;

public final class EnchantingMixins {
    private EnchantingMixins() {}
    @Mixin(net.minecraft.world.level.block.entity.EnchantingTableBlockEntity.class)
    public static abstract class TableHum implements EnchantingClient.TableAudioAccess {
        @Unique private final EnchantingClient.TableAudio naturality$audio = new EnchantingClient.TableAudio();
        @Override public EnchantingClient.TableAudio naturality$tableAudio() { return naturality$audio; }

        @Inject(method = "bookAnimationTick", at = @At("TAIL"))
        private static void naturality$hum(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos position,
                net.minecraft.world.level.block.state.BlockState state,
                net.minecraft.world.level.block.entity.EnchantingTableBlockEntity table, CallbackInfo ci) {
            ((EnchantingClient.TableAudioAccess)table).naturality$tableAudio().tick(table);
        }
    }

    @Mixin(net.minecraft.client.gui.render.GuiRenderer.class)
    public static abstract class ImbuingItemSilhouette {
        @Inject(method = "submitBlitFromItemAtlas", at = @At("TAIL"))
        private void naturality$captureInscription(net.minecraft.client.renderer.state.gui.GuiItemRenderState item,
                net.minecraft.client.gui.render.GuiItemAtlas.SlotView slot, CallbackInfo ci) {
            if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof EnchantingClient.Screen screen)
                screen.captureInscriptionItem(item, slot);
        }
    }
    @Mixin(net.minecraft.client.renderer.blockentity.state.EnchantTableRenderState.class)
    public static abstract class TableItemState implements naturality.client.systems.enchanting.TableItemRendering.Access {
        @Unique private final naturality.client.systems.enchanting.TableItemRendering.Display naturality$items =
            new naturality.client.systems.enchanting.TableItemRendering.Display();
        @Override public naturality.client.systems.enchanting.TableItemRendering.Display naturality$display() { return naturality$items; }
    }
    @Mixin(net.minecraft.client.renderer.blockentity.EnchantTableRenderer.class)
    public static abstract class TableItems {
        @Inject(method = "extractRenderState(Lnet/minecraft/world/level/block/entity/EnchantingTableBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/EnchantTableRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V", at = @At("TAIL"))
        private void naturality$extractItems(net.minecraft.world.level.block.entity.EnchantingTableBlockEntity entity,
                net.minecraft.client.renderer.blockentity.state.EnchantTableRenderState state, float partialTick,
                net.minecraft.world.phys.Vec3 camera, net.minecraft.client.renderer.feature.ModelFeatureRenderer.@Nullable CrumblingOverlay breaking,
                CallbackInfo ci) {
            ((naturality.client.systems.enchanting.TableItemRendering.Access)state).naturality$display().extract(entity, partialTick);
        }
        @Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/EnchantTableRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("TAIL"))
        private void naturality$submitItems(net.minecraft.client.renderer.blockentity.state.EnchantTableRenderState state,
                PoseStack pose, SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
            ((naturality.client.systems.enchanting.TableItemRendering.Access)state).naturality$display().submit(pose, collector, state.lightCoords);
        }
    }
    @Mixin(ItemStack.class)
    public static abstract class BookTooltips {
        @Inject(method = "addToTooltip(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/item/component/TooltipDisplay;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;)V",
            at = @At("HEAD"), cancellable = true)
        private void naturality$enchantmentIcons(DataComponentType<?> type, Item.TooltipContext context, TooltipDisplay display,
                Consumer<Component> consumer, TooltipFlag flag, CallbackInfo ci) {
            if (type != DataComponents.STORED_ENCHANTMENTS && type != DataComponents.ENCHANTMENTS) return;
            var item = (ItemStack)(Object)this;
            ci.cancel();
            if (!display.shows(type)) return;
            // Retain searchable/narratable text; only the visual tooltip replaces these rows.
            var enchantmentType = type == DataComponents.STORED_ENCHANTMENTS ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS;
            EnchantingClient.tooltipLabels(item, enchantmentType)
                .forEach(label -> consumer.accept(label.copy()
                    .withStyle(s -> s.withInsertion("naturality:enchantment_tooltip"))));
            if (type == DataComponents.ENCHANTMENTS && !item.is(Items.ENCHANTED_BOOK) && item.has(Enchanting.PAIRS)) {
                consumer.accept(Component.literal("Enchantment slots: " + (item.getOrDefault(Enchanting.PAIRS, java.util.List.of()).size() + Enchanting.standaloneSlots(item))
                    + "/" + item.getOrDefault(Enchanting.CAPACITY, 3)).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
        }
        @ModifyReturnValue(method = "getTooltipImage", at = @At("RETURN"))
        private Optional<TooltipComponent> naturality$details(Optional<TooltipComponent> original) {
            return EnchantingClient.tooltip((ItemStack)(Object)this, original);
        }
    }
    @Mixin(ClientTooltipComponent.class)
    public interface EnchantmentTooltipFactory {
        @Inject(method = "create(Lnet/minecraft/world/inventory/tooltip/TooltipComponent;)Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;",
            at = @At("HEAD"), cancellable = true)
        private static void naturality$component(TooltipComponent component, CallbackInfoReturnable<ClientTooltipComponent> cir) {
            if (component instanceof EnchantingClient.EnchantmentTooltip tooltip) cir.setReturnValue(tooltip);
        }
    }
    @Mixin(ItemModelResolver.class)
    public static abstract class OpenBookModel {
        @Inject(method = "appendItemLayers", at = @At("HEAD"))
        private void naturality$iconState(ItemStackRenderState output, ItemStack item, ItemDisplayContext context,
                net.minecraft.world.level.@Nullable Level level, net.minecraft.world.entity.@Nullable ItemOwner owner, int seed, CallbackInfo ci) {
            boolean visible = BookIconRendering.visible(item);
            ((BookIconRendering.IconState)output).naturality$bookIcon(visible, visible ? EnchantmentIcons.book(item) : EnchantmentIcons.MISSING, context);
        }
        @ModifyExpressionValue(method = "appendItemLayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
        private @Nullable Object naturality$openBook(@Nullable Object original, ItemStackRenderState output, ItemStack item, ItemDisplayContext context) {
            return item.is(Items.ENCHANTED_BOOK) && item.getOrDefault(Enchanting.REVEALED, false) ? Naturality.id("deciphered_book") : original;
        }
    }

    @Mixin(ItemStackRenderState.class)
    public static abstract class BookIconState implements BookIconRendering.IconState {
        @Unique private boolean naturality$hasBookIcon;
        @Unique private Identifier naturality$iconTexture = EnchantmentIcons.MISSING;
        @Unique private ItemDisplayContext naturality$iconContext = ItemDisplayContext.NONE;
        @Override public void naturality$bookIcon(boolean visible, Identifier texture, ItemDisplayContext context) {
            naturality$hasBookIcon = visible;
            naturality$iconTexture = texture;
            naturality$iconContext = context;
        }
        @Inject(method = "clear", at = @At("TAIL"))
        private void naturality$clearIcon(CallbackInfo ci) { naturality$hasBookIcon = false; }
        @Inject(method = "submit", at = @At("TAIL"))
        private void naturality$submitIcon(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline, CallbackInfo ci) {
            if (naturality$hasBookIcon) BookIconRendering.submit((ItemStackRenderState)(Object)this, naturality$iconTexture, naturality$iconContext, pose, collector);
        }
    }

    @Mixin(net.minecraft.client.gui.Hud.class)
    public static abstract class HotbarBookIcons {
        @WrapMethod(method = "extractRenderState")
        private void naturality$deferIcons(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker,
                Operation<Void> original) {
            BookIconRendering.hud(graphics, () -> original.call(graphics, deltaTracker));
        }
    }

    @Mixin(GuiGraphicsExtractor.class)
    public static abstract class InventoryBookIcon {
        @WrapMethod(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;Z)V")
        private void naturality$visualDetails(Font font, List<Component> texts, Optional<TooltipComponent> image,
                int x, int y, @Nullable Identifier style, boolean extraSpace, Operation<Void> original) {
            var visible = image.orElse(null) instanceof EnchantingClient.EnchantmentTooltip
                ? texts.stream().filter(t -> !"naturality:enchantment_tooltip".equals(t.getStyle().getInsertion())).toList() : texts;
            original.call(font, visible, image, x, y, style, extraSpace);
        }
        @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V", at = @At("TAIL"))
        private void naturality$overlay(net.minecraft.world.entity.@Nullable LivingEntity owner,
                net.minecraft.world.level.@Nullable Level level, ItemStack item, int x, int y, int seed, CallbackInfo ci) {
            BookIconRendering.inventory((GuiGraphicsExtractor)(Object)this, item, x, y);
        }
    }
}
