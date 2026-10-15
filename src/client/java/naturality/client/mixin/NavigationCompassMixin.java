package naturality.client.mixin;

import naturality.client.navigation.NavigationClient;
import net.minecraft.client.renderer.item.properties.numeric.CompassAngleState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CompassAngleState.class)
public abstract class NavigationCompassMixin {
    @Redirect(method = "calculate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/properties/numeric/CompassAngleState$CompassTarget;get(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/ItemOwner;)Lnet/minecraft/core/GlobalPos;"))
    private @org.jspecify.annotations.Nullable GlobalPos naturality$target(CompassAngleState.CompassTarget kind, ClientLevel level, ItemStack stack, ItemOwner owner) {
        return kind == CompassAngleState.CompassTarget.SPAWN ? NavigationClient.target(level) : kind.get(level, stack, owner);
    }
}
