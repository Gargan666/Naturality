package naturality.client.mixin;

import naturality.client.villager.VillagerVisualState;
import naturality.villager.VillagerWorkVisuals;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerRenderer.class)
public abstract class VillagerVisualExtractMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void naturality$extract(Villager entity, VillagerRenderState state, float partialTicks, CallbackInfo ci) {
        var data = (VillagerVisualState)state;
        data.naturality$setOwner(entity);
        boolean toolPose = isWorkTool(entity.getMainHandItem())
            && !((VillagerWorkVisuals)entity).naturality$isDisplayingTrade();
        data.naturality$setToolPose(toolPose);
        data.naturality$setFishingRodPose(toolPose && entity.getMainHandItem().is(Items.FISHING_ROD));
        if (toolPose) ((LivingEntityItemResolverAccess)this).naturality$itemModelResolver().updateForLiving(
            state.heldItem, entity.getMainHandItem(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
    }

    private static boolean isWorkTool(ItemStack stack) {
        return stack.has(DataComponents.TOOL) || stack.is(ItemTags.SWORDS) || stack.is(Items.FISHING_ROD)
            || stack.is(Items.SHEARS) || stack.is(Items.BRUSH);
    }
}

