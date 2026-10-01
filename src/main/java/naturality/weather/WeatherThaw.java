package naturality.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Bounded surface sampling in chunks already being ticked; never loads chunks. */
public final class WeatherThaw {
    private WeatherThaw() {}
    public static float chance(float temperature) {
        float warmth = Math.clamp((temperature - 50) / 50, 0, 1);
        return warmth * warmth;
    }
    public static void tick(ServerLevel level, LevelChunk chunk, int tickSpeed) {
        var weather = WeatherSystem.state(level);
        if (!naturality.config.NaturalityServerConfig.get().weatherThaw || tickSpeed <= 0 || weather == null) return;
        var random = level.getRandom();
        float snowChance = chance(weather.temperature());
        for (int i=0; i<8; i++) {
            int x=chunk.getPos().getMinBlockX()+random.nextInt(16);
            int z=chunk.getPos().getMinBlockZ()+random.nextInt(16);
            var surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x,0,z));
            // Thin snow may sit above the heightmap; ice and thick snow are below it.
            boolean thawed = random.nextFloat() < snowChance && thawAt(level, surface);
            if (!thawed) thawed = random.nextFloat() < snowChance && thawAt(level, surface.below());
            if (!thawed) {
                // Snow above signs/other non-motion-blocking shapes lives above
                // the ordinary weather heightmap but must still thaw normally.
                var visible = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x,0,z)).below();
                if (visible.getY() > surface.getY() && random.nextFloat() < snowChance)
                    thawed = thawAt(level, visible);
            }
            if (!thawed) {
                // Ice melts as soon as the custom climate classifies this biome
                // as rainy, independent of the faster high-temperature thaw roll.
                thawRainIceAt(level, surface);
                thawRainIceAt(level, surface.below());
                thawRainIceAt(level, surface.below(2));
                var visible = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x,0,z)).below();
                if (visible.getY() > surface.getY()) thawRainIceAt(level, visible);
            }
        }
    }
    public static boolean thawAt(ServerLevel level, BlockPos pos) {
        var weather = WeatherSystem.state(level);
        if (!naturality.config.NaturalityServerConfig.get().weatherThaw || weather == null || weather.temperature() <= 50 || !naturality.util.LoadedChunks.has(level, pos)) return false;
        var block = level.getBlockState(pos);
        if (!block.is(Blocks.SNOW) && !block.is(Blocks.ICE)) return false;
        if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) > pos.getY()+1
                || WeatherSystem.precipitation(level,
                level.getBiome(pos).value(), pos) == Biome.Precipitation.SNOW) return false;
        if (block.is(Blocks.SNOW)) {
            int layers = block.getValue(SnowLayerBlock.LAYERS);
            if (layers > 1) level.setBlockAndUpdate(pos, block.setValue(SnowLayerBlock.LAYERS, layers-1));
            else level.removeBlock(pos, false);
        } else {
            level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
            level.neighborChanged(pos, Blocks.WATER, null);
        }
        return true;
    }

    private static boolean thawRainIceAt(ServerLevel level, BlockPos pos) {
        var weather = WeatherSystem.state(level);
        if (!naturality.config.NaturalityServerConfig.get().weatherThaw || weather == null
                || !naturality.util.LoadedChunks.has(level, pos)
                || !level.getBlockState(pos).is(Blocks.ICE)
                || WeatherSystem.precipitation(level, level.getBiome(pos).value(), pos) != Biome.Precipitation.RAIN)
            return false;
        level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        level.neighborChanged(pos, Blocks.WATER, null);
        return true;
    }
}

