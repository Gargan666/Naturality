package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.client.sky.EndFlashes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.EndFlashState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.class)
public abstract class EndFlashSoundMixin {
    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/client/renderer/EndFlashState"))
    private EndFlashState naturality$eventState(Operation<EndFlashState> original) {
        var level = (ClientLevel)(Object)this;
        return level.dimension().equals(Level.END) ? new EndFlashes.State(level) : original.call();
    }
}
