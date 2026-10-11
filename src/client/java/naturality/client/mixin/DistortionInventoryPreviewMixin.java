package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.client.weather.GravityPreviewContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(InventoryScreen.class)
public abstract class DistortionInventoryPreviewMixin {
    @WrapMethod(method="extractRenderState(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;")
    private static EntityRenderState naturality$inventoryOrientation(LivingEntity entity,Operation<EntityRenderState> original) {
        boolean previous=GravityPreviewContext.enter();
        try{return original.call(entity);}finally{GravityPreviewContext.restore(previous);}
    }
}