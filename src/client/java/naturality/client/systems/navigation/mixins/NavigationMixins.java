package naturality.client.systems.navigation.mixins;

import naturality.client.systems.navigation.NavigationClient;
import net.minecraft.client.renderer.item.properties.numeric.CompassAngleState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class NavigationMixins {
    private NavigationMixins() {}

    @Mixin(CompassAngleState.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class NavigationCompassMixin {
        @WrapOperation(method = "calculate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/properties/numeric/CompassAngleState$CompassTarget;get(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/ItemOwner;)Lnet/minecraft/core/GlobalPos;"))
        private @org.jspecify.annotations.Nullable GlobalPos naturality$target(CompassAngleState.CompassTarget kind,
                ClientLevel level, ItemStack stack, ItemOwner owner, Operation<@org.jspecify.annotations.Nullable GlobalPos> original) {
            var target = original.call(kind, level, stack, owner);
            return kind == CompassAngleState.CompassTarget.SPAWN ? NavigationClient.target(level) : target;
        }
    }

    @Mixin(Hud.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class NavigationHudMixin {
        @Shadow private int overlayMessageTime;
        @Inject(method = "extractOverlayMessage", at = @At("HEAD"))
        private void naturality$readout(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
            if (overlayMessageTime > 0) return;
            var message = NavigationClient.message();
            if (message == null) return;
            var font = Minecraft.getInstance().font;
            int width = font.width(message);
            graphics.nextStratum();
            graphics.pose().pushMatrix();
            graphics.pose().translate(graphics.guiWidth() / 2, graphics.guiHeight() - 68);
            graphics.textWithBackdrop(font, message, -width / 2, -4, width, 0xFFFFFFFF);
            graphics.pose().popMatrix();
        }
    }
}
