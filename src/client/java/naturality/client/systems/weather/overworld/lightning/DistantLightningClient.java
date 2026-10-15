package naturality.client.systems.weather.overworld.lightning;

import java.util.ArrayList;
import naturality.systems.weather.overworld.lightning.DistantLightning.DistantLightningPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.core.BlockPos;
import naturality.Naturality;
import com.mojang.renderpearl.api.pipeline.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.*;
import naturality.config.live.NaturalityConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import naturality.client.systems.weather.framework.WeatherClient.WeatherSoundEnvironment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import naturality.systems.weather.framework.WeatherSystem.WeatherShelter;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.util.RandomSource;

@org.jspecify.annotations.NullMarked
public final class DistantLightningClient {
    private static final ArrayList<LightningBolt> BOLTS=new ArrayList<>();
    private static int nextId=-1000000;
    private static @org.jspecify.annotations.Nullable ClientLevel world;
    private DistantLightningClient() {}
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(DistantLightningPayload.TYPE,(p,c) -> c.client().execute(() -> receive(p)));
        naturality.client.systems.sleep.SleepVisuals.register(client -> {
            if(world!=client.level) { BOLTS.clear();world=client.level; }
            if(client.isPaused())return;
            BOLTS.removeIf(bolt -> { bolt.commonTick();bolt.tick();return bolt.isRemoved(); });
        });
    }
    public static void receive(DistantLightningPayload p) {
        if (!naturality.config.startup.FeatureConfig.Feature.LIGHTNING.enabled()) return;
        var client=Minecraft.getInstance();var level=client.level;
        if(level==null || !level.dimension().identifier().equals(p.dimension()))return;
        if(world!=level) { BOLTS.clear();world=level; }
        var bolt=EntityTypes.LIGHTNING_BOLT.create(level,EntitySpawnReason.EVENT);if(bolt==null)return;
        var camera=client.gameRenderer.mainCamera().position();
        var pos=BlockPos.containing(p.x(),p.seaLevel()+1,p.z());
        boolean distant=Math.hypot(p.x()-camera.x,p.z()-camera.z)>client.options.getEffectiveRenderDistance()*16;
        int y=distant || !naturality.api.worlds.LoadedChunks.has(level,pos)?p.seaLevel()+1:
            level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,pos.getX(),pos.getZ());
        bolt.setId(nextId--);bolt.snapTo(new net.minecraft.world.phys.Vec3(p.x(),y,p.z()));bolt.seed=p.seed();bolt.setVisualOnly(true);
        if(BOLTS.size()>=64)BOLTS.removeFirst();BOLTS.add(bolt);
    }
    public static void extract(LevelRenderState output,float partial) {
        var client=Minecraft.getInstance();if(world!=client.level)return;
        for(var bolt:BOLTS)output.entityRenderStates.add(client.getEntityRenderDispatcher().extractEntity(bolt,partial));
    }

    public static final class LightningRendering {
        private static final RenderPipeline.Snippet SHADERS=RenderPipeline.builder()
            .withVertexShader(Naturality.id("core/weather_lightning")).withFragmentShader(Naturality.id("core/weather_lightning")).buildSnippet();
        private static final RenderPipeline PIPELINE=RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET,SHADERS)
            .withLocation(Naturality.id("pipeline/lightning")).withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING)).build());
        private static final OitPipelineSet OIT=RenderPipelines.register(OitPipelineSet.builder("naturality_lightning",
            RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET,SHADERS).withShaderDefine("OIT_ADDITIVE")).build());
        public static final RenderType TYPE=RenderType.create("naturality_lightning",RenderSetup.builder(PIPELINE).setOitPipelines(OIT).sortOnUpload().createRenderSetup());
        private LightningRendering() {}
    }

    /** One impact pulse, with an independently smooth, low-contrast accessibility alternative. */
    public static final class LightningFlashes {
        private static long start;
        private static double simulationStart;
        private static @org.jspecify.annotations.Nullable Object world;
        private static double hold=.4;
        private static double decay=1;
        private LightningFlashes() {}
        public static void initialize() {
            naturality.client.systems.sleep.SleepVisuals.register(client->{
                if(world!=client.level || start!=0 && elapsed(reduced())>Math.max(1,.1+hold+decay)){world=null;start=0;}
            });
        }
        public static void strike() {
            var random=java.util.concurrent.ThreadLocalRandom.current();
            hold=.4+random.nextDouble(-.2,.2);
            decay=1+random.nextDouble(-.2,.2);
            world=Minecraft.getInstance().level;start=System.nanoTime();
            simulationStart=naturality.client.systems.sleep.SleepVisuals.seconds();
        }
        private static boolean reduced() {
            return NaturalityConfig.get().epilepsyMode || Minecraft.getInstance().options.hideLightningFlash().get();
        }
        private static double elapsed(boolean reduced) {
            return reduced ? (System.nanoTime()-start)/1_000_000_000.0
                : naturality.client.systems.sleep.SleepVisuals.seconds()-simulationStart;
        }
        public static float opacity(double seconds,boolean reduced) {
            return opacity(seconds,reduced,hold,decay);
        }
        public static float opacity(double seconds,boolean reduced,double holdSeconds,double decaySeconds) {
            if(seconds<0)return 0;
            if(reduced) {
                float in=(float)Math.clamp(seconds/.2,0,1),out=(float)Math.clamp((1.0-seconds)/.8,0,1);
                return .075F*in*in*(3-2*in)*out*out*(3-2*out);
            }
            if(seconds<.1)return .9F*smooth(seconds/.1);
            if(seconds<=.1+holdSeconds)return .9F;
            return .9F*(1-smooth((seconds-.1-holdSeconds)/decaySeconds));
        }
        private static float smooth(double value) {
            float t=(float)Math.clamp(value,0,1);
            return t*t*(3-2*t);
        }
        public static void extract(GuiGraphicsExtractor graphics) {
            var client=Minecraft.getInstance();if(start==0 || world!=client.level || client.level==null)return;
            boolean reduced=reduced();
            float alpha=opacity(elapsed(reduced),reduced);
            if(alpha<=0)return;
            int color=reduced?ARGB.colorFromFloat(alpha,.18F,.36F,1):ARGB.colorFromFloat(alpha,.86F,.93F,1);
            graphics.fill(0,0,graphics.guiWidth(),graphics.guiHeight(),color);
        }
    }

    /** Each listener schedules thunder locally, including multiplayer visual strikes. */
    public static final class ThunderAudio {
        private record Pending(Vec3 position, SoundEvent sound, SoundSource source,
                               float volume, float pitch, long due) {}
        private static final ArrayList<Pending> pending = new ArrayList<>();
        private static @org.jspecify.annotations.Nullable ClientLevel world;
        private static long ticks;
        private static final class ThunderSound extends AbstractTickableSoundInstance {
            private final ClientLevel level;
            private final float gain;
            ThunderSound(ClientLevel level, Pending p) {
                super(p.sound, p.source, RandomSource.create());
                this.level = level;
                gain = volume = p.volume;
                pitch = p.pitch;
                x = p.position.x; y = p.position.y; z = p.position.z;
            }
            @Override public void tick() {
                if (Minecraft.getInstance().level != level) { stop(); return; }
                volume = WeatherSoundEnvironment.underground() ? 0 : gain;
            }
        }
        private ThunderAudio() {}
        public static int delayTicks(double distance) { return (int)Math.round(distance * 20 / 343); }
        public static float pitchScale(double distance) { return (float)(1 / (1 + distance / 1024)); }
        public static boolean audible() {
            var c = Minecraft.getInstance();
            var entity = c.getCameraEntity();
            var level = c.level;
            return level != null && entity != null && !WeatherShelter.underground(level,
                c.gameRenderer.mainCamera().position(), entity);
        }
        public static void schedule(ClientLevel level, Vec3 position, SoundEvent sound, SoundSource source,
                                    float volume, float pitch) {
            if (world != level) { pending.clear(); world = level; ticks = 0; }
            if (!audible()) return;
            double distance = position.distanceTo(Minecraft.getInstance().gameRenderer.mainCamera().position());
            if (pending.size() >= 256) pending.removeFirst();
            pending.add(new Pending(position, sound, source, volume, pitch * pitchScale(distance), ticks + delayTicks(distance)));
        }
        public static void initialize() {
            ClientTickEvents.END_CLIENT_TICK.register(c -> {
                var level = c.level;
                if (world != level) { pending.clear(); world = level; ticks = 0; }
                if (c.isPaused() || level == null) return;
                ticks++;
                pending.removeIf(p -> {
                    if (p.due > ticks) return false;
                    if (audible()) c.getSoundManager().play(new ThunderSound(level, p));
                    return true;
                });
            });
        }
    }
}
