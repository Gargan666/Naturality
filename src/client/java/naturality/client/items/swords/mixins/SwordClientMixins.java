package naturality.client.items.swords.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.client.items.swords.SwordCounterClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Input wiring stays separate from common item use and damage authority. */
@NullMarked
public final class SwordClientMixins {
    private SwordClientMixins() {}

    @Mixin(Minecraft.class)
    public static abstract class CounterPress {
        @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;startUseItem()V", ordinal = 0))
        private void naturality$freshPress(Minecraft client, Operation<Void> original) {
            // The first call handles consumeClick(); the second is vanilla's held-button repeat.
            SwordCounterClient.onPress(() -> original.call(client));
        }
    }

    @Mixin(MultiPlayerGameMode.class)
    public static abstract class CounterUsePacket {
        @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
        private void naturality$noRepeatedUse(Player player, InteractionHand hand,
                CallbackInfoReturnable<InteractionResult> result) {
            if (!SwordCounterClient.allowUse(player, hand)) result.setReturnValue(InteractionResult.FAIL);
        }
    }
}
