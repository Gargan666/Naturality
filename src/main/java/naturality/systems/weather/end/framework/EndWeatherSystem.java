package naturality.systems.weather.end.framework;

import naturality.api.worlds.EnvironmentWorldData;
import naturality.api.worlds.PreviewEnvironment;
import java.util.WeakHashMap;
import java.util.Collections;
import net.minecraft.server.MinecraftServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import naturality.api.random.SavedRandom;
import java.util.HashMap;
import java.util.Map;
import naturality.Naturality;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

@org.jspecify.annotations.NullMarked
public final class EndWeatherSystem {
    private static final String KEY = "weather/minecraft:the_end/gravity_cycle";
    private static final Map<ServerLevel, EndWeatherCycle> cycles = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<MinecraftServer, Long> SESSIONS = Collections.synchronizedMap(new WeakHashMap<>());
    private record ClientState(long session, EndWeatherState from, EndWeatherState target, long time) {}
    private static volatile ClientState client = new ClientState(0,EndWeatherState.CLEAR,EndWeatherState.CLEAR,0);
    private EndWeatherSystem() {}
    public static EndWeatherCycle cycle(ServerLevel level) {
        var existing = cycles.get(level);
        if (existing != null) return existing;
        var cycle = new EndWeatherCycle(level.getSeed() ^ 0x454e44475241564cL);
        cycle.restore(EnvironmentWorldData.get(java.util.Objects.requireNonNull(level.getServer())).read(KEY));
        cycles.put(level, cycle);
        return cycle;
    }
    public static void save(ServerLevel level) { EnvironmentWorldData.get(java.util.Objects.requireNonNull(level.getServer())).defer(KEY,cycle(level)::snapshot); }
    public static EndWeatherState state(Level level) {
        if (!naturality.config.startup.FeatureConfig.Feature.END_WEATHER.enabled()) return EndWeatherState.CLEAR;
        if (!level.dimension().equals(Level.END)) return EndWeatherState.CLEAR;
        return level instanceof ServerLevel s ? cycle(s).state() : client.target;
    }
    public static EndWeatherState renderState(Level level) {
        if (!naturality.config.startup.FeatureConfig.Feature.END_WEATHER.enabled()) return EndWeatherState.CLEAR;
        if (!level.dimension().equals(Level.END) || !level.isClientSide()) return state(level);
        var c = client;
        float t = Math.clamp((System.nanoTime()-c.time)/100_000_000F,0,1);
        return new EndWeatherState(c.from.gravity()+(c.target.gravity()-c.from.gravity())*t,c.target.starfall(),c.from.rise()+(c.target.rise()-c.from.rise())*t);
    }
    public static void clearClient() { client = new ClientState(0,EndWeatherState.CLEAR,EndWeatherState.CLEAR,0); }
    public static void receive(EndWeatherPayload p) {
        var previous = client;
        float t = Math.clamp((System.nanoTime()-previous.time)/100_000_000F,0,1);
        var from = p.session() == previous.session ? new EndWeatherState(
            previous.from.gravity()+(previous.target.gravity()-previous.from.gravity())*t,p.state().starfall(),previous.from.rise()+(previous.target.rise()-previous.from.rise())*t) : p.state();
        client = new ClientState(p.session(),from,p.state(),System.nanoTime());
    }
    private static void send(net.minecraft.server.level.ServerPlayer player, ServerLevel level) {
        if (ServerPlayNetworking.canSend(player,EndWeatherPayload.TYPE)) {
            long session = SESSIONS.computeIfAbsent(java.util.Objects.requireNonNull(level.getServer()),
                _ -> java.util.UUID.randomUUID().getLeastSignificantBits());
            ServerPlayNetworking.send(player,new EndWeatherPayload(session,cycle(level).state()));
        }
    }
    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(EndWeatherPayload.TYPE,EndWeatherPayload.CODEC);
        if (!naturality.config.startup.FeatureConfig.Feature.END_WEATHER.enabled()) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> SESSIONS.put(server, java.util.UUID.randomUUID().getLeastSignificantBits()));
        ServerLifecycleEvents.SERVER_STOPPED.register(EndWeatherSystem::clearServer);
        ServerPlayConnectionEvents.JOIN.register((handler,_,s) -> { var end=s.getLevel(Level.END); if(end!=null)send(handler.player,end); });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            var end = s.getLevel(Level.END); if (end == null) return;
            var cycle = cycle(end);
            if (!PreviewEnvironment.frozen(s)) cycle.tick(!end.players().isEmpty() && end.getGameRules().get(GameRules.ADVANCE_WEATHER), PreviewEnvironment.rate(s));
            if (!PreviewEnvironment.frozen(s) && naturality.config.startup.FeatureConfig.Feature.STARFALL.enabled()) naturality.systems.weather.end.starfall.StarfallWeather.tick(end,cycle.state().starfall());
            save(end);
            if (s.getTickCount()%2 == 0) for (var p:s.getPlayerList().getPlayers()) send(p,end);
        });
    }
    private static void clearServer(MinecraftServer server) {
        for (var level : server.getAllLevels()) cycles.remove(level);
        SESSIONS.remove(server);
    }

    /** Saved End weather clocks. Legacy distortion fields are intentionally ignored. */
    public static final class EndWeatherCycle {
        private final SavedRandom random;
        private int gravityOverride = -1, starfallOverride = -1;
        private int starfallRemaining, starfallRetarget;
        private float starfall, starfallTarget;
        private int riseOverride=-1,riseRemaining,riseRetarget;
        private float rise,riseTarget;
        private int quiet = 6000, gravityRemaining, retarget;
        private float gravity, gravityTarget;
        public EndWeatherCycle(long seed) { random = new SavedRandom(seed); }
        public int override(String channel) {
            return switch(channel) { case "gravity" -> gravityOverride; case "starfall" -> starfallOverride; case "rise" -> riseOverride; default -> throw new IllegalArgumentException(channel); };
        }
        public void set(String channel, int value) {
            switch(channel) {
                case "gravity" -> gravityOverride = value;
                case "starfall" -> starfallOverride = value;
                case "rise" -> riseOverride = value;
                default -> throw new IllegalArgumentException(channel);
            }
        }
        public EndWeatherState state() { return new EndWeatherState(gravity, Math.round(starfall),rise); }
        public void tick(boolean advance) { tick(advance, 1); }
        public void tick(boolean advance, int rate) {
            if (advance) {
                if (quiet > 0) quiet--;
                if(riseRemaining>0) {
                    riseRemaining--;
                    if(--riseRetarget<=0) {riseTarget=20+random.nextFloat()*80;riseRetarget=400+random.nextInt(800);}
                } else {
                    riseTarget=0;
                    if(quiet==0 && random.nextInt(18000)<rate) {riseRemaining=2400+random.nextInt(3600);riseRetarget=0;}
                }
                if (gravityRemaining > 0) {
                    gravityRemaining--;
                    if (--retarget <= 0) { gravityTarget = 20 + random.nextFloat()*80; retarget = 400 + random.nextInt(800); }
                } else {
                    gravityTarget = 0;
                    if (quiet == 0 && random.nextInt(12000) < rate) { gravityRemaining = 2400 + random.nextInt(3600); retarget = 0; }
                }
            }
            // Natural starfall is temporarily disabled; auto always returns it to calm.
            starfallRemaining=starfallRetarget=0;starfallTarget=0;
            if(advance || starfallOverride>=0 || starfall>0) starfall+=Math.clamp((starfallOverride>=0?starfallOverride:0)-starfall,-.5F*rate,.5F*rate);
            if(advance || riseOverride>=0)rise+=Math.clamp((riseOverride>=0?riseOverride:riseTarget)-rise,-.5F*rate,.5F*rate);
            float target = gravityOverride >= 0 ? gravityOverride : gravityTarget;
            if (advance || gravityOverride >= 0) gravity += Math.clamp(target - gravity, -.25F*rate, .25F*rate);
        }
        public Map<String,Long> snapshot() {
            var data = new HashMap<String,Long>();
            data.put("random", random.state()); data.put("gravityOverride", (long)gravityOverride);
            data.put("starfall",EnvironmentWorldData.bits(starfall));
            data.put("starfallVersion",1L);data.put("starfallOverride",(long)starfallOverride);
            data.put("starfallRemaining",(long)starfallRemaining);data.put("starfallRetarget",(long)starfallRetarget);
            data.put("starfallTarget",EnvironmentWorldData.bits(starfallTarget)); data.put("quiet", (long)quiet);
            data.put("gravityRemaining", (long)gravityRemaining); data.put("retarget", (long)retarget);
            data.put("gravity", EnvironmentWorldData.bits(gravity)); data.put("gravityTarget", EnvironmentWorldData.bits(gravityTarget));
            data.put("rise",EnvironmentWorldData.bits(rise));data.put("riseTarget",EnvironmentWorldData.bits(riseTarget));
            data.put("riseOverride",(long)riseOverride);data.put("riseRemaining",(long)riseRemaining);data.put("riseRetarget",(long)riseRetarget);
            return data;
        }
        public void restore(Map<String,Long> data) {
            if (data.isEmpty()) return;
            random.restore(data.getOrDefault("random", random.state()));
            gravityOverride = data.getOrDefault("gravityOverride", -1L).intValue();
            if(data.containsKey("starfallVersion")) {
                starfall=EnvironmentWorldData.number(data,"starfall");starfallOverride=data.getOrDefault("starfallOverride",-1L).intValue();
                starfallRemaining=data.getOrDefault("starfallRemaining",0L).intValue();starfallRetarget=data.getOrDefault("starfallRetarget",0L).intValue();
                starfallTarget=EnvironmentWorldData.number(data,"starfallTarget");
            } else {starfall=data.getOrDefault("starfall",0L).intValue();starfallOverride=starfall>0?(int)starfall:-1;} quiet = data.getOrDefault("quiet",6000L).intValue();
            gravityRemaining = data.getOrDefault("gravityRemaining",0L).intValue(); retarget = data.getOrDefault("retarget",0L).intValue();
            if(starfallOverride<0)starfall=0;starfallRemaining=starfallRetarget=0;starfallTarget=0;
            rise=EnvironmentWorldData.number(data,"rise");riseTarget=EnvironmentWorldData.number(data,"riseTarget");
            riseOverride=data.getOrDefault("riseOverride",-1L).intValue();riseRemaining=data.getOrDefault("riseRemaining",0L).intValue();riseRetarget=data.getOrDefault("riseRetarget",0L).intValue();
            gravity = EnvironmentWorldData.number(data,"gravity"); gravityTarget = EnvironmentWorldData.number(data,"gravityTarget");
        }
    }

    /** Dimension weather changes gravity strength; Distortion is an individual effect. */
    public record EndWeatherState(float gravity, int starfall, float rise) {
        public static final EndWeatherState CLEAR = new EndWeatherState(0, 0, 0);
        public float strength() { return 1 - .9F * Math.clamp(gravity / 100, 0, 1); }
        public float verticalGravity() { return strength(); }
    }

    public record EndWeatherPayload(long session, EndWeatherState state) implements CustomPacketPayload {
        public static final Type<EndWeatherPayload> TYPE = new Type<>(Naturality.id("end_weather"));
        @SuppressWarnings("null") // StreamCodec predates null contracts; network buffers/payloads are required.
        public static final StreamCodec<RegistryFriendlyByteBuf, EndWeatherPayload> CODEC = new StreamCodec<>() {
            public EndWeatherPayload decode(RegistryFriendlyByteBuf b) {
                return new EndWeatherPayload(b.readLong(),new EndWeatherState(b.readFloat(),b.readVarInt(),b.readFloat()));
            }
            public void encode(RegistryFriendlyByteBuf b, EndWeatherPayload p) {
                b.writeLong(p.session); b.writeFloat(p.state.gravity()); b.writeVarInt(p.state.starfall());b.writeFloat(p.state.rise());
            }
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
