package naturality.client.weather;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import naturality.config.NaturalityConfig;
import naturality.weather.WeatherSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Client-thread scratch data, valid only during a particle update, spawn or render pass. */
public final class WeatherParticleContext {
    private static final int MAX_ENTRIES = 8192;
    private static final Long2IntOpenHashMap HEIGHTS = new Long2IntOpenHashMap();
    private static final Long2IntOpenHashMap TINTS = new Long2IntOpenHashMap();
    private static final Long2IntOpenHashMap LIGHTS = new Long2IntOpenHashMap();
    private static final Long2ByteOpenHashMap SKY=new Long2ByteOpenHashMap();
    private static final Long2ObjectOpenHashMap<Biome.Precipitation> PRECIPITATION = new Long2ObjectOpenHashMap<>();
    private static @org.jspecify.annotations.Nullable ClientLevel world;
    private static @org.jspecify.annotations.Nullable State state;
    private static @org.jspecify.annotations.Nullable Wind wind;
    static {
        HEIGHTS.defaultReturnValue(Integer.MAX_VALUE);
        TINTS.defaultReturnValue(Integer.MIN_VALUE);
        LIGHTS.defaultReturnValue(Integer.MIN_VALUE);
    }

    private WeatherParticleContext() { }

    public record State(boolean enabled, double rainWindX, double rainWindZ,
                        double snowWindX, double snowWindZ, float snowFallMultiplier, Vec3 eye, int radius,
                        float heavyFog) {
        public float heavyParticleChance(double x,double y,double z) {
            if(heavyFog==0)return 0;
            double dx=x-eye.x,dy=y-eye.y,dz=z-eye.z;
            double squared=dx*dx+dy*dy+dz*dz;
            return squared>=225?heavyFog:heavyFog*(float)(Math.sqrt(squared)/15);
        }
    }
    public record Wind(boolean enabled,double x,double z,@org.jspecify.annotations.Nullable Entity camera) { }
    public static Wind wind(ClientLevel level) {
        var cached=wind;
        if(world==level && cached!=null)return cached;
        var weather=WeatherSystem.state(level);
        var result=new Wind(weather!=null && NaturalityConfig.get().effects.particleWind,
            weather==null?0:weather.windX()*.007,weather==null?0:weather.windZ()*.007,Minecraft.getInstance().getCameraEntity());
        if(world==level)wind=result;
        return result;
    }

    public static void begin(ClientLevel level) {
        end();
        world = level;
    }

    public static void end() {
        world = null;
        state = null;
        wind = null;
        HEIGHTS.clear();
        TINTS.clear();
        LIGHTS.clear();
        SKY.clear();
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
            weather == null ? 1 : weather.snowFallMultiplier(),
            client.gameRenderer.mainCamera().position(), ParticleWeather.radius(client),weather==null?0:weather.heavyRainFog());
        if (world == level) state = result;
        return result;
    }

    public static int waterTint(ClientLevel level, BlockPos pos) {
        if (world != level) return BiomeColors.getAverageWaterColor(level, pos);
        long key = pos.asLong();
        int cached=TINTS.get(key);
        if(cached!=Integer.MIN_VALUE)return cached;
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

    /** Exact-position light shared for one extraction pass, including dynamic lighting hooks. */
    public static int light(ClientLevel level,BlockPos pos) {
        long key=pos.asLong();
        if(world==level) {
            int cached=LIGHTS.get(key);
            if(cached!=Integer.MIN_VALUE)return cached;
        }
        int light=naturality.util.LoadedChunks.has(level,pos)?LightCoordsUtil.getLightCoords(level,pos):15728640;
        if(world==level && LIGHTS.size()<MAX_ENTRIES)LIGHTS.put(key,light);
        return light;
    }
    public static boolean canSeeSky(ClientLevel level,BlockPos pos) {
        if(world!=level)return level.canSeeSky(pos);
        long key=pos.asLong();
        byte cached=SKY.get(key);
        if(cached!=0)return cached==2;
        boolean visible=level.canSeeSky(pos);
        if(SKY.size()<MAX_ENTRIES)SKY.put(key,(byte)(visible?2:1));
        return visible;
    }

    /** Integer.MIN_VALUE means absent; getChunkNow never loads a chunk. */
    public static int floor(ClientLevel level, int x, int z) {
        long key = ((long)x << 32) ^ (z & 0xffffffffL);
        if(world==level) {
            int cached=HEIGHTS.get(key);
            if(cached!=Integer.MAX_VALUE)return cached;
        }
        var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        int height = chunk == null ? Integer.MIN_VALUE
            : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING,x&15,z&15)+1;
        if (world == level && HEIGHTS.size() < MAX_ENTRIES) HEIGHTS.put(key, height);
        return height;
    }
}
