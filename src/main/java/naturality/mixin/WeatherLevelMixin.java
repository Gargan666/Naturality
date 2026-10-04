package naturality.mixin;

import naturality.weather.WeatherSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class WeatherLevelMixin {
    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void naturality$rain(float partial, CallbackInfoReturnable<Float> cir) {
        var s = WeatherSystem.renderState((Level)(Object)this);
        if (s != null) cir.setReturnValue(s.rainLevel());
    }
    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void naturality$thunder(float partial, CallbackInfoReturnable<Float> cir) {
        var s = WeatherSystem.renderState((Level)(Object)this);
        if (s != null) cir.setReturnValue(s.thunderLevel());
    }
    @Inject(method = "isRaining", at = @At("HEAD"), cancellable = true)
    private void naturality$wet(CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level)(Object)this;
        var s = WeatherSystem.state(level);
        if (s != null) cir.setReturnValue(level.canHaveWeather() && s.rain() > 0);
    }
    @Inject(method = "isThundering", at = @At("HEAD"), cancellable = true)
    private void naturality$storm(CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level)(Object)this;
        var s = WeatherSystem.state(level);
        if (s != null) cir.setReturnValue(level.canHaveWeather() && s.rain() >= 50);
    }
    @Redirect(method = "precipitationAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation naturality$climate(Biome biome, BlockPos pos, int seaLevel) {
        return WeatherSystem.precipitation((Level)(Object)this, biome, pos);
    }
}
