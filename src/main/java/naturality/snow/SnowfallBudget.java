package naturality.snow;

import java.util.WeakHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import naturality.config.Feature;
import naturality.config.GameplaySettings;
import naturality.config.NaturalityServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

import net.minecraft.world.level.block.state.BlockState;

/** Server-thread snowfall backpressure. Natural randomized precipitation retries skipped columns. */
public final class SnowfallBudget {
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
                && naturality.weather.WeatherSystem.state(level)!=null;
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
