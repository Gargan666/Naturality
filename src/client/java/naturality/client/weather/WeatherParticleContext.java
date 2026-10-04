package naturality.client.weather;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import naturality.config.NaturalityConfig;
import naturality.weather.WeatherSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Client-thread scratch data, valid only during a particle update or spawn pass. */
public final class WeatherParticleContext {
    private static final int MAX_ENTRIES = 8192;
    private static final Long2IntOpenHashMap HEIGHTS = new Long2IntOpenHashMap();
    private static final Long2IntOpenHashMap TINTS = new Long2IntOpenHashMap();
    private static final Long2ObjectOpenHashMap<Biome.Precipitation> PRECIPITATION = new Long2ObjectOpenHashMap<>();
    private static @org.jspecify.annotations.Nullable ClientLevel world;
    private static @org.jspecify.annotations.Nullable State state;

    private WeatherParticleContext() { }

    public record State(boolean enabled, double rainWindX, double rainWindZ,
                        double snowWindX, double snowWindZ, Vec3 eye, int radius) { }

    public static void begin(ClientLevel level) {
        end();
        world = level;
    }

    public static void end() {
        world = null;
        state = null;
        HEIGHTS.clear();
        TINTS.clear();
        PRECIPITATION.clear();
    }

    public static State state(ClientLevel level) {
        var cached = state;
        if (world == level && cached != null) return cached;
        var client = Minecraft.getInstance();
        var weather = WeatherSystem.state(level);
        // Keep the original double multiplication (weather wind components are floats).
        double windX = weather == null ? 0 : weather.windX();
        double windZ = weather == null ? 0 : weather.windZ();
        var result = new State(client.level == level && NaturalityConfig.get().effects.weatherParticles
            && level.canHaveWeather() && weather != null && weather.rain() > 0,
            windX * .34, windZ * .34, windX * .10, windZ * .10,
            client.gameRenderer.mainCamera().position(), ParticleWeather.radius(client));
        if (world == level) state = result;
        return result;
    }

    public static int waterTint(ClientLevel level, BlockPos pos) {
        if (world != level) return BiomeColors.getAverageWaterColor(level, pos);
        long key = pos.asLong();
        if (TINTS.containsKey(key)) return TINTS.get(key);
        int color = BiomeColors.getAverageWaterColor(level, pos);
        if (TINTS.size() < MAX_ENTRIES) TINTS.put(key, color);
        return color;
    }

    public static Biome.Precipitation precipitation(ClientLevel level, BlockPos pos) {
        if (world != level) return level.getPrecipitationAt(pos);
        long key = pos.asLong();
        var cached = PRECIPITATION.get(key);
        if (cached != null) return cached;
        var kind = level.getPrecipitationAt(pos);
        if (PRECIPITATION.size() < MAX_ENTRIES) PRECIPITATION.put(key, kind);
        return kind;
    }

    /** Integer.MIN_VALUE means absent; getChunkNow never loads a chunk. */
    public static int floor(ClientLevel level, int x, int z) {
        long key = ((long)x << 32) ^ (z & 0xffffffffL);
        if (world == level && HEIGHTS.containsKey(key)) return HEIGHTS.get(key);
        var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        int height = chunk == null ? Integer.MIN_VALUE
            : level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (world == level && HEIGHTS.size() < MAX_ENTRIES) HEIGHTS.put(key, height);
        return height;
    }
}
