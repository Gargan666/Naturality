package naturality.sky.shared.aurora;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import naturality.systems.weather.framework.WeatherSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.gamerules.GameRules;
import naturality.api.random.SavedRandom;

/** Shared state per occupied 1024-block region; never generates chunks to sample climate. */
@org.jspecify.annotations.NullMarked
public final class AuroraEvents {
    private record Region(int x, int z) {
        static Region at(BlockPos pos) { return new Region(Math.floorDiv(pos.getX(), 1024), Math.floorDiv(pos.getZ(), 1024)); }
    }
    private static final class Cell {
        final AuroraCycle cycle;
        long lastSeen;
        float chance;
        Cell(long seed) { cycle = new AuroraCycle(seed); }
    }
    private static final Map<ServerLevel, Map<Region, Cell>> STATES = new WeakHashMap<>();
    private AuroraEvents() {}
    public static void clear() { STATES.clear(); }
    private static String prefix(ServerLevel level) { return "aurora/"+level.dimension().identifier()+"/"; }
    public static void restore(ServerLevel level) {
        var cells=STATES.computeIfAbsent(level,_ -> new HashMap<>());
        naturality.api.worlds.EnvironmentWorldData.get(java.util.Objects.requireNonNull(level.getServer())).entries().forEach((key,saved) -> {
            if(!key.startsWith(prefix(level)))return;
            var region=new Region(saved.getOrDefault("x",0L).intValue(),saved.getOrDefault("z",0L).intValue());
            var cell=new Cell(0);cell.cycle.restore(saved);
            cell.lastSeen=saved.getOrDefault("lastSeen",level.getGameTime());
            cell.chance=naturality.api.worlds.EnvironmentWorldData.number(saved,"chance");
            cells.put(region,cell);
        });
    }
    public static float strength(ServerLevel level, BlockPos pos) {
        var cells = STATES.get(level);
        var cell = cells == null ? null : cells.get(Region.at(pos));
        return cell == null ? 0 : cell.cycle.strength();
    }
    public record Status(boolean active, float strength, int nextAttempt, float chance) {}
    public static @org.jspecify.annotations.Nullable Status status(ServerLevel level, BlockPos pos) {
        var cells = STATES.get(level);
        var cell = cells == null ? null : cells.get(Region.at(pos));
        return cell == null ? null : new Status(cell.cycle.active(), cell.cycle.strength(),
            cell.cycle.nextAttempt(), cell.chance);
    }
    public static void tick(ServerLevel level) {
        var cells = STATES.computeIfAbsent(level, _ -> new HashMap<>());
        var occupied = new HashMap<Region, Float>();
        boolean end = level.dimension().equals(net.minecraft.world.level.Level.END);
        var weather = WeatherSystem.state(level);
        float temperature = weather == null ? 50 : weather.temperature();
        for (var player : level.players()) {
            var pos = player.blockPosition();
            var biome = level.getBiome(pos).value();
            float chance = end ? AuroraCycle.END_START_CHANCE : AuroraCycle.startChance(biome.getBaseTemperature(),
                biome.getPrecipitationAt(pos, level.getSeaLevel()) == Biome.Precipitation.SNOW, temperature);
            // More players in one region never multiply the number of random attempts.
            occupied.merge(Region.at(pos), chance, Math::max);
        }
        long time = level.getGameTime();
        boolean advance = end || level.getGameRules().get(GameRules.ADVANCE_WEATHER);
        occupied.forEach((region, chance) -> {
            var cell = cells.computeIfAbsent(region, _ -> new Cell(level.getSeed()
                ^ (region.x * 341873128712L) ^ (region.z * 132897987541L) ^ time));
            cell.lastSeen = time;
            cell.chance = chance;
            cell.cycle.tick(advance, (float)naturality.api.worlds.PreviewEnvironment.chance(chance, naturality.api.worlds.PreviewEnvironment.rate(java.util.Objects.requireNonNull(level.getServer()))));
            var saved=new HashMap<>(cell.cycle.snapshot());
            saved.put("x",(long)region.x);saved.put("z",(long)region.z);
            saved.put("lastSeen",cell.lastSeen);saved.put("chance",naturality.api.worlds.EnvironmentWorldData.bits(chance));
            naturality.api.worlds.EnvironmentWorldData.get(java.util.Objects.requireNonNull(level.getServer())).write(prefix(level)+region.x+","+region.z,saved);
        });
        // Vacated regions do not accumulate events. Forget them after one minute.
        cells.entrySet().removeIf(entry -> {
            if(time-entry.getValue().lastSeen<=1200)return false;
            naturality.api.worlds.EnvironmentWorldData.get(java.util.Objects.requireNonNull(level.getServer())).remove(prefix(level)+entry.getKey().x+","+entry.getKey().z);
            return true;
        });
    }

    /** Rare local events at any time of day; climate changes the chance of starting. */
    public static final class AuroraCycle {
        public static final float END_START_CHANCE = (float)(1 - Math.sqrt(.9));
        private final SavedRandom random;
        private int nextAttempt, remaining, retarget;
        private float strength, target;
        public AuroraCycle(long seed) { random = new SavedRandom(seed); nextAttempt = 200 + random.nextInt(401); }
        public java.util.Map<String,Long> snapshot() {
            return java.util.Map.of("random",random.state(),"nextAttempt",(long)nextAttempt,"remaining",(long)remaining,"retarget",(long)retarget,"strength",naturality.api.worlds.EnvironmentWorldData.bits(strength),"target",naturality.api.worlds.EnvironmentWorldData.bits(target));
        }
        public void restore(java.util.Map<String,Long> data) {
            if(data.isEmpty())return;
            random.restore(data.getOrDefault("random",random.state()));
            nextAttempt=data.getOrDefault("nextAttempt",0L).intValue();
            remaining=data.getOrDefault("remaining",0L).intValue();
            retarget=data.getOrDefault("retarget",0L).intValue();
            strength=naturality.api.worlds.EnvironmentWorldData.number(data,"strength");
            target=naturality.api.worlds.EnvironmentWorldData.number(data,"target");
        }
        public float strength() { return strength; }
        public boolean active() { return remaining > 0; }
        public int nextAttempt() { return nextAttempt; }
        public static float startChance(float biomeTemperature, boolean snowy, float weatherTemperature) {
            float biomeCold = Math.clamp((.8F - biomeTemperature) / 1.3F + (snowy ? .35F : 0), 0, 1);
            // At weather temperature 0 in a mild plains biome, two 600-tick attempts
            // have approximately a 15% combined chance of starting an aurora.
            float weatherBoost = (float)Math.pow(2, (50 - Math.clamp(weatherTemperature, 0, 100)) / 8.77F);
            return .0015F * (1 + 8 * biomeCold) * weatherBoost;
        }
        public void tick(boolean advance, float chance) {
            if (!advance) return;
            if (remaining > 0) {
                remaining--;
                if (--retarget <= 0) { target = 4 + random.nextFloat() * 16; retarget = 200 + random.nextInt(401); }
                if (remaining == 0) { target = 0; nextAttempt = 2400; }
            } else if (--nextAttempt <= 0) {
                nextAttempt = 600;
                if (random.nextFloat() < Math.clamp(chance, 0, 1)) {
                    remaining = 2400 + random.nextInt(4801);
                    retarget = 0;
                }
            }
            strength += (target - strength) * .005F;
            if (Math.abs(target - strength) < .001F) strength = target;
        }
    }
}
