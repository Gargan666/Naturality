package naturality.systems.weather.framework.mixins;

import naturality.systems.weather.framework.WeatherSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import com.mojang.brigadier.CommandDispatcher;
import naturality.systems.weather.framework.WeatherSystem.WeatherCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.WeatherCommand;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.server.level.ServerLevel;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class WeatherFrameworkMixins {
    private WeatherFrameworkMixins() {}

    /** World context is explicit: shared biome instances never store a dimension's temperature. */
    @Mixin(Biome.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WeatherBiomeMixin {
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

    /** Replace registration rather than merge incompatible vanilla duration arguments. */
    @Mixin(WeatherCommand.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WeatherCommandMixin {
        @Inject(method = "register", at = @At("HEAD"), cancellable = true)
        private static void naturality$sliders(CommandDispatcher<CommandSourceStack> dispatcher, CallbackInfo ci) {
            WeatherCommands.register(dispatcher);
            ci.cancel();
        }
    }

    @Mixin(Level.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WeatherLevelMixin {
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
            if (s != null) cir.setReturnValue(level.canHaveWeather() && s.rain() > 70);
        }
        @Redirect(method = "precipitationAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
        private Biome.Precipitation naturality$climate(Biome biome, BlockPos pos, int seaLevel) {
            return WeatherSystem.precipitation((Level)(Object)this, biome, pos);
        }
    }

    @Mixin(ServerLevel.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WeatherServerMixin {
        @ModifyVariable(method = "tickPrecipitation", at = @At("STORE"), ordinal = 0)
        private int naturality$snowLimit(int vanillaLimit) {
            return naturality.systems.weather.overworld.snow.WeatherSnow.accumulationLimit((ServerLevel)(Object)this, vanillaLimit);
        }
        @Inject(method = "tickChunk", at = @At("TAIL"))
        private void naturality$thaw(net.minecraft.world.level.chunk.LevelChunk chunk, int tickSpeed,
                org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
            naturality.systems.weather.overworld.snow.WeatherSnow.WeatherThaw.tick((ServerLevel)(Object)this, chunk, tickSpeed);
        }
        @Redirect(method = "tickPrecipitation", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
        private Biome.Precipitation naturality$precipitation(Biome biome, BlockPos pos, int seaLevel) {
            return WeatherSystem.precipitation((ServerLevel)(Object)this, biome, pos);
        }
    }
}
