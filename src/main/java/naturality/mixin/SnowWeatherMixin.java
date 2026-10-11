package naturality.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import naturality.snow.SnowfallBudget;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(ServerLevel.class)
public abstract class SnowWeatherMixin {
    @WrapOperation(method="tickPrecipitation",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean naturality$budgetSnow(net.minecraft.world.level.biome.Biome biome,net.minecraft.world.level.LevelReader reader,
            BlockPos pos,Operation<Boolean> original) {
        ServerLevel level=(ServerLevel)(Object)this;
        if(!SnowfallBudget.active(level))return original.call(biome,reader,pos);
        if(naturality.weather.WeatherSystem.precipitation(level,biome,pos)
                !=net.minecraft.world.level.biome.Biome.Precipitation.SNOW)return original.call(biome,reader,pos);
        var old=level.getBlockState(pos);
        int cap=Math.min(8,naturality.weather.WeatherSnow.accumulationLimit(level,
            level.getGameRules().get(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT)));
        if(old.is(Blocks.SNOW) && old.getValue(SnowLayerBlock.LAYERS)>=cap)return false;
        if(!SnowfallBudget.tryAttempt(level,pos))return false;
        long started=System.nanoTime();
        try {return original.call(biome,reader,pos);} finally {SnowfallBudget.recordWork(level,started);}
    }
    @WrapOperation(method="tickPrecipitation",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;pushEntitiesUp(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState naturality$deferPush(BlockState old,BlockState snow,net.minecraft.world.level.LevelAccessor level,
            BlockPos pos,Operation<BlockState> original) {
        // The budgeted write below pushes only if the write is actually admitted.
        return SnowfallBudget.active((ServerLevel)level)?snow:original.call(old,snow,level,pos);
    }
    @WrapOperation(method="tickPrecipitation",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean naturality$writeSnow(ServerLevel level,BlockPos pos,BlockState state,Operation<Boolean> original) {
        return state.is(Blocks.SNOW) && SnowfallBudget.active(level)
            ?SnowfallBudget.grow(level,pos,state):original.call(level,pos,state);
    }
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
        int layers=old.is(Blocks.SNOW)?old.getValue(SnowLayerBlock.LAYERS):0;
        if(layers>=max)return;
        if(naturality.snow.NoSnowBlocks.contains(old) || !SnowfallBudget.tryAttempt(level,surface))return;
        long started=System.nanoTime();
        boolean survives;
        try {
            // Only a possible eighth layer needs a separate geometry search.
            if(max==8)max=naturality.snow.SnowGeometry.maxLayers(level,surface);
            survives=layers<max && Blocks.SNOW.defaultBlockState().canSurvive(level,surface);
        }
        finally {SnowfallBudget.recordWork(level,started);}
        if(!survives)return;
        var snow=Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,layers+1);
        SnowfallBudget.grow(level,surface,snow);
    }
}
