package naturality.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class SnowWeatherMixin {
    @Inject(method="tickPrecipitation",at=@At("RETURN"))
    private void naturality$irregularSnow(BlockPos column, CallbackInfo ci) {
        ServerLevel level=(ServerLevel)(Object)this;
        if(!naturality.config.GameplaySettings.snowWrapping(level) || !level.isRaining())return;
        var ordinary=level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,column);
        var surface=level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE,column);
        if(level.getBlockState(surface.below()).getBlock() instanceof BaseFireBlock)
            surface=surface.below();
        if(surface.getY()<=ordinary.getY()
                && !(level.getBlockState(surface).getBlock() instanceof BaseFireBlock))return;
        if(level.getBlockState(surface.below()).is(Blocks.SNOW))surface=surface.below();
        if(surface.equals(ordinary)
                && !(level.getBlockState(surface).getBlock() instanceof BaseFireBlock))return;
        int max=Math.min(8,naturality.weather.WeatherSnow.accumulationLimit(level,
            level.getGameRules().get(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT)));
        if(max<=0 || naturality.weather.WeatherSystem.precipitation(level,
                level.getBiome(surface).value(),surface)
                !=net.minecraft.world.level.biome.Biome.Precipitation.SNOW
                || level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,surface)>=10)return;
        var old=level.getBlockState(surface);
        if(naturality.snow.NoSnowBlocks.contains(old)
                || !Blocks.SNOW.defaultBlockState().canSurvive(level,surface))return;
        int layers=old.is(Blocks.SNOW)?old.getValue(SnowLayerBlock.LAYERS):0;
        if(layers>=max)return;
        var snow=Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,layers+1);
        Block.pushEntitiesUp(old,snow,level,surface);
        level.setBlockAndUpdate(surface,snow);
    }
}
