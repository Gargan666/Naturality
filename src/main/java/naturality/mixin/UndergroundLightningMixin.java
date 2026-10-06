package naturality.mixin;

import naturality.weather.WeatherShelter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class UndergroundLightningMixin {
    @Inject(method = "tickThunder", at = @At("HEAD"), cancellable = true)
    private void naturality$surfaceStorm(LevelChunk chunk, CallbackInfo ci) {
        var level = (ServerLevel)(Object)this;
        if (level.isRaining() && level.isThundering() && !WeatherShelter.hasSurfacePlayer(level)) ci.cancel();
    }
}
