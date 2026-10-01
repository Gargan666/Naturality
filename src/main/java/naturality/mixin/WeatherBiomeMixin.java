package naturality.mixin;

import naturality.weather.WeatherSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** World context is explicit: shared biome instances never store a dimension's temperature. */
@Mixin(Biome.class)
public abstract class WeatherBiomeMixin {
    @Redirect(method = "shouldSnow", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation naturality$snow(Biome biome, BlockPos pos, int seaLevel, LevelReader reader, BlockPos ignored) {
        return reader instanceof Level level ? WeatherSystem.precipitation(level, biome, pos) : biome.getPrecipitationAt(pos, seaLevel);
    }
    @Redirect(method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;warmEnoughToRain(Lnet/minecraft/core/BlockPos;I)Z"))
    private boolean naturality$freeze(Biome biome, BlockPos pos, int seaLevel, LevelReader reader, BlockPos ignored, boolean neighbors) {
        if (reader instanceof Level level && WeatherSystem.state(level) != null)
            return WeatherSystem.precipitation(level, biome, pos) != Biome.Precipitation.SNOW;
        return biome.warmEnoughToRain(pos, seaLevel);
    }
}
