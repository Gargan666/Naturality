package naturality.systems.weather.overworld.snow;

import naturality.systems.weather.framework.WeatherSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.WeakHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import naturality.config.startup.FeatureConfig.Feature;
import naturality.config.gameplay.GameplaySettings;
import naturality.config.gameplay.GameplaySettings.NaturalityServerConfig;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Rain-channel thresholds for snowfall accumulation. */
@org.jspecify.annotations.NullMarked
public final class WeatherSnow {
    private WeatherSnow() {}
    public static int accumulationLimit(net.minecraft.server.level.ServerLevel level, int vanillaLimit) {
        var weather = WeatherSystem.state(level);
        if (!naturality.config.gameplay.GameplaySettings.NaturalityServerConfig.get().weatherSnowAccumulation || weather == null || vanillaLimit <= 0) return vanillaLimit;
        return Math.min(7, layerLimit(weather.rain()));
    }
    public static int layerLimit(float rain) {
        if (rain >= 80) return Integer.MAX_VALUE;
        if (rain >= 60) return 4;
        if (rain >= 50) return 2;
        return 1;
    }

    /** Bounded surface sampling in chunks already being ticked; never loads chunks. */
    public static final class WeatherThaw {
        private WeatherThaw() {}
        public static float chance(float temperature) {
            float warmth = Math.clamp((temperature - 50) / 50, 0, 1);
            return warmth * warmth;
        }
        public static void tick(ServerLevel level, LevelChunk chunk, int tickSpeed) {
            var weather = WeatherSystem.state(level);
            if (!naturality.config.gameplay.GameplaySettings.NaturalityServerConfig.get().weatherThaw || tickSpeed <= 0 || weather == null) return;
            var random = level.getRandom();
            float snowChance = chance(weather.temperature());
            for (int i=0; i<8; i++) {
                int x=chunk.getPos().getMinBlockX()+random.nextInt(16);
                int z=chunk.getPos().getMinBlockZ()+random.nextInt(16);
                var surface = new BlockPos(x, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) + 1, z);
                // Thin snow may sit above the heightmap; ice and thick snow are below it.
                boolean thawed = random.nextFloat() < snowChance && thawAt(level, chunk, surface, weather.temperature());
                if (!thawed) thawed = random.nextFloat() < snowChance && thawAt(level, chunk, surface.below(), weather.temperature());
                if (!thawed) {
                    // Snow above signs/other non-motion-blocking shapes lives above
                    // the ordinary weather heightmap but must still thaw normally.
                    var visible = new BlockPos(x, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15), z);
                    if (visible.getY() > surface.getY() && random.nextFloat() < snowChance)
                        thawed = thawAt(level, chunk, visible, weather.temperature());
                }
                if (!thawed) {
                    // Ice melts as soon as the custom climate classifies this biome
                    // as rainy, independent of the faster high-temperature thaw roll.
                    thawRainIceAt(level, chunk, surface);
                    thawRainIceAt(level, chunk, surface.below());
                    thawRainIceAt(level, chunk, surface.below(2));
                    var visible = new BlockPos(x, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15), z);
                    if (visible.getY() > surface.getY()) thawRainIceAt(level, chunk, visible);
                }
            }
        }
        public static boolean thawAt(ServerLevel level, BlockPos pos) {
            var weather = WeatherSystem.state(level);
            if (!naturality.config.gameplay.GameplaySettings.NaturalityServerConfig.get().weatherThaw || weather == null || weather.temperature() <= 50 || !naturality.api.worlds.LoadedChunks.has(level, pos)) return false;
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            return chunk != null && thawAt(level, chunk, pos, weather.temperature());
        }
        private static boolean thawAt(ServerLevel level, LevelChunk chunk, BlockPos pos, float temperature) {
            if (temperature <= 50) return false;
            var block = chunk.getBlockState(pos);
            if (!block.is(Blocks.SNOW) && !block.is(Blocks.ICE)) return false;
            if (chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX() & 15, pos.getZ() & 15) + 1 > pos.getY()+1
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

        private static boolean thawRainIceAt(ServerLevel level, LevelChunk chunk, BlockPos pos) {
            // The caller already checked the setting and weather for this chunk tick.
            if (!chunk.getBlockState(pos).is(Blocks.ICE)
                    || WeatherSystem.precipitation(level, level.getBiome(pos).value(), pos) != Biome.Precipitation.RAIN)
                return false;
            level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
            level.neighborChanged(pos, Blocks.WATER, null);
            return true;
        }
    }

    /** Server-thread snowfall backpressure. Natural randomized precipitation retries skipped columns. */
    public static final class SnowfallBudget {
        public static final int MAX_ATTEMPTS=32, MAX_CHANGES=8, MAX_SECTIONS=4;
        public static final long MAX_WORK_NANOS=1_000_000;
        private static final WeakHashMap<ServerLevel,Budget> LEVELS=new WeakHashMap<>();
        private static final class Budget {
            long tick=Long.MIN_VALUE,work;
            int attempts,changes,seen,start;
            final LongOpenHashSet sections=new LongOpenHashSet(MAX_SECTIONS);
        }
        private SnowfallBudget() {}
        public static boolean active(ServerLevel level) {
            return GameplaySettings.snowWrapping(level)
                || Feature.WEATHER_CYCLES.enabled() && NaturalityServerConfig.get().weatherSnowAccumulation
                    && naturality.systems.weather.framework.WeatherSystem.state(level)!=null;
        }
        private static Budget budget(ServerLevel level) {
            var budget=LEVELS.computeIfAbsent(level,_ -> new Budget());
            long tick=level.getGameTime();
            if(budget.tick!=tick) {
                // The chunk tick iterator need not shuffle. Rotate the admitted
                // window so a large storm cannot indefinitely favor its first chunks.
                budget.start=budget.seen==0?0:(budget.start+Math.max(1,budget.attempts))%budget.seen;
                budget.seen=0;
                budget.tick=tick;budget.work=0;budget.attempts=budget.changes=0;budget.sections.clear();
            }
            return budget;
        }
        private static boolean available(Budget budget,BlockPos pos) {
            return budget.changes<MAX_CHANGES
                && (budget.sections.size()<MAX_SECTIONS || budget.sections.contains(SectionPos.asLong(pos)));
        }
        /** Called before the expensive survival/shape checks, not just before setting a block. */
        public static boolean tryAttempt(ServerLevel level,BlockPos pos) {
            if(!active(level))return true;
            var budget=budget(level);
            if(budget.seen++<budget.start || budget.attempts>=MAX_ATTEMPTS
                    || budget.attempts>0 && budget.work>=MAX_WORK_NANOS || !available(budget,pos))return false;
            budget.attempts++;
            return true;
        }
        public static void recordWork(ServerLevel level,long started) {
            if(active(level))budget(level).work+=Math.max(0,System.nanoTime()-started);
        }
        public static boolean grow(ServerLevel level,BlockPos pos,BlockState snow) {
            boolean limited=active(level);
            var budget=limited?budget(level):null;
            // Let the first admitted change finish even if its preflight exhausted
            // the soft timer, so expensive supports can still accumulate snow.
            if(budget != null && (!available(budget,pos) || budget.changes>0 && budget.work>=MAX_WORK_NANOS))return false;
            long started=System.nanoTime();
            try {
                var old=level.getBlockState(pos);
                if(old==snow)return false;
                // Keep collision pushing before the real block update, including fitted snow.
                Block.pushEntitiesUp(old,snow,level,pos);
                boolean changed=level.setBlockAndUpdate(pos,snow);
                if(changed && budget != null) {
                    budget.changes++;
                    budget.sections.add(SectionPos.asLong(pos));
                }
                return changed;
            } finally {recordWork(level,started);}
        }
    }
}
