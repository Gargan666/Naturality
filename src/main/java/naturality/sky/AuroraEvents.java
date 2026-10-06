package naturality.sky;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import naturality.weather.WeatherSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.gamerules.GameRules;

/** Shared state per occupied 1024-block region; never generates chunks to sample climate. */
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
        naturality.weather.EnvironmentWorldData.get(level.getServer()).entries().forEach((key,saved) -> {
            if(!key.startsWith(prefix(level)))return;
            var region=new Region(saved.getOrDefault("x",0L).intValue(),saved.getOrDefault("z",0L).intValue());
            var cell=new Cell(0);cell.cycle.restore(saved);
            cell.lastSeen=saved.getOrDefault("lastSeen",level.getGameTime());
            cell.chance=naturality.weather.EnvironmentWorldData.number(saved,"chance");
            cells.put(region,cell);
        });
    }
    public static float strength(ServerLevel level, BlockPos pos) {
        var cells = STATES.get(level);
        var cell = cells == null ? null : cells.get(Region.at(pos));
        return cell == null ? 0 : cell.cycle.strength();
    }
    public record Status(boolean active, float strength, int nextAttempt, float chance) {}
    public static Status status(ServerLevel level, BlockPos pos) {
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
            cell.cycle.tick(advance, chance);
            var saved=new HashMap<>(cell.cycle.snapshot());
            saved.put("x",(long)region.x);saved.put("z",(long)region.z);
            saved.put("lastSeen",cell.lastSeen);saved.put("chance",naturality.weather.EnvironmentWorldData.bits(chance));
            naturality.weather.EnvironmentWorldData.get(level.getServer()).write(prefix(level)+region.x+","+region.z,saved);
        });
        // Vacated regions do not accumulate events. Forget them after one minute.
        cells.entrySet().removeIf(entry -> {
            if(time-entry.getValue().lastSeen<=1200)return false;
            naturality.weather.EnvironmentWorldData.get(level.getServer()).remove(prefix(level)+entry.getKey().x+","+entry.getKey().z);
            return true;
        });
    }
}
