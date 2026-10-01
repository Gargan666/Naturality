package naturality.mixin;

import naturality.weather.WeatherSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ServerLevel.class)
public abstract class WeatherServerMixin {
    @ModifyVariable(method = "tickPrecipitation", at = @At("STORE"), ordinal = 0)
    private int naturality$snowLimit(int vanillaLimit) {
        return naturality.weather.WeatherSnow.accumulationLimit((ServerLevel)(Object)this, vanillaLimit);
    }
    @Inject(method = "tickChunk", at = @At("TAIL"))
    private void naturality$thaw(net.minecraft.world.level.chunk.LevelChunk chunk, int tickSpeed,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        naturality.weather.WeatherThaw.tick((ServerLevel)(Object)this, chunk, tickSpeed);
    }
    @Redirect(method = "tickPrecipitation", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation naturality$precipitation(Biome biome, BlockPos pos, int seaLevel) {
        return WeatherSystem.precipitation((ServerLevel)(Object)this, biome, pos);
    }
}
