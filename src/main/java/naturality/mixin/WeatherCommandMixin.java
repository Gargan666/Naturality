package naturality.mixin;

import com.mojang.brigadier.CommandDispatcher;
import naturality.weather.WeatherCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.WeatherCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace registration rather than merge incompatible vanilla duration arguments. */
@Mixin(WeatherCommand.class)
public abstract class WeatherCommandMixin {
    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private static void naturality$sliders(CommandDispatcher<CommandSourceStack> dispatcher, CallbackInfo ci) {
        WeatherCommands.register(dispatcher);
        ci.cancel();
    }
}
